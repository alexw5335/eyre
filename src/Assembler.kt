package eyre

import java.util.Stack
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

class Assembler(private val context: Context) {


	private lateinit var file: SrcFile

	private var writer = context.textWriter

	private var section = context.textSec

	private var nodeIndex = 0

	private var currentFun: FunNode? = null

	private var currentSrcPos: SrcPos? = null

	private fun invalid(message: String = "Invalid encoding"): Nothing = throw EyreError(currentSrcPos, message)

	private fun err(node: Node, message: String): Nothing = throw EyreError(node.srcPos, message)

	private fun err(srcPos: SrcPos?, message: String): Nothing = throw EyreError(srcPos, message)

	private fun err(message: String = "No reason given"): Nothing = throw EyreError(currentSrcPos, message)

	private fun section(section: Section) {
		this.section = section
		this.writer = section.writer!!
	}

	private var addrMem = Mem()

	private fun loc(node: Node): VarLoc = if(node is CallNode) node.loc!! else (node.exprSym as VarNode).loc!!




	/*
	Assembly
	 */



	fun assembleFile(file: SrcFile) {
		this.nodeIndex = 0
		this.file = file

		try {
			assembleStringLiterals()
			assembleScope()
		} catch(e: EyreError) {
			context.errors.add(e)
			file.invalid = true
		}
	}



	private fun assembleScope() {
		while(nodeIndex < file.nodes.size) {
			val node = file.nodes[nodeIndex++]
			if(node !is ScopeEndNode)
				currentSrcPos = node.srcPos
			when(node) {
				is ScopeEndNode  -> return
				is NamespaceNode -> assembleScope()
				is FunNode       -> assembleFunction(node)
				is VarNode       -> assembleGlobalVar(node)
				is CallNode      -> genCall(node)
				is StructNode    -> { }
				is BinNode       -> assembleBinNode(node)
				else             -> err("Invalid node: $node")
			}
		}
	}



	private fun assembleStringLiterals() {
		section(context.dataSec)
		for(sym in context.stringLiterals) {
			writer.align(8)
			sym.pos.sec = section
			sym.pos.disp = writer.pos
			writer.asciiNT(sym.value)
		}
	}



	private fun assembleGlobalVar(node: VarNode) {
		val reloc = (node.loc as GlobalVarLoc).reloc

		if(node.valueNode == null) {
			context.bssSize = context.bssSize.align(node.type.alignment)
			reloc.sec = context.bssSec
			reloc.disp = context.bssSize
			context.bssSize += node.size
		} else {
			section(context.dataSec)
			writer.align(node.type.alignment)
			reloc.sec = section
			reloc.disp = writer.pos
			writer.ensureCapacity(node.type.size)
			writeInitialiser(node.type, node.valueNode)
			writer.pos += node.type.size
		}
	}



	private fun writeInitialiser(type: Type, node: Node) {
		if(node is InitNode) {
			if(type is StructNode) {
				if(node.elements.size > type.members.size)
					err(node, "Too many initialisers")
				for(i in node.elements.indices) {
					val member = type.members[i]
					writer.at(writer.pos + member.offset) {
						writeInitialiser(member.type, node.elements[i])
					}
				}
			} else if(type is ArrayType) {
				if(node.elements.size > type.count)
					err(node, "Too many initialiser elements")
				for(i in node.elements.indices) {
					writer.at(writer.pos + type.type.size * i) {
						writeInitialiser(type.type, node.elements[i])
					}
				}
			} else if(type is PointerType) {
				for(i in node.elements.indices) {
					writer.at(writer.pos + type.type.size * i) {
						writeInitialiser(type.type, node.elements[i])
					}
				}
			} else {
				err(node, "Invalid initialiser type: ${type.name}")
			}
		} else {
			if(type is IntType) {
				writeImm(node, type.width)
			} else if(type is ArrayType) {
				val string = (node as? StringNode)?.value ?: invalid()
				if(string.length + 1 > type.size) invalid()
				for(c in string) {
					if(c.code > Byte.MAX_VALUE) invalid()
					writer.i8(c.code)
				}
				writer.i8(0)
			} else if(type is PointerType) {
				val sym = (node as? StringNode)?.litSym ?: invalid()
				context.absRelocs.add(Reloc(SecPos(section, writer.pos), sym.pos, 0, Width.NONE, Width.NONE))
				writer.i64(0)
			} else {
				err(node, "Invalid initialiser")
			}
		}
	}



	private fun writeImm(node: Node, width: Width) {
		fun rec(node: Node, regValid: Boolean): Long {
			if(node.isConst) return node.constValue

			fun sym(sym: Sym?): Long {
				if(sym == null) invalid()
				invalid()
			}

			return when(node) {
				is BinNode  -> node.calc(regValid, ::rec)
				is UnNode   -> node.calc(regValid, ::rec)
				is IntNode  -> node.value
				is NameNode -> sym(node.exprSym)
				else        -> invalid()
			}
		}

		val value = rec(node, true)
		if(!writer.writeWidth(width, value))
			err(node, "value out of range")
	}



	private fun assembleFunction(function: FunNode) {
		if(function.isDllImport) return
		section(context.textSec)
		currentFun = function

		writer.pos += 21 // make room for prologue
		writer.align(16)
		val startPos = writer.pos

		function.stackPos = -32
		for(param in function.params) {
			function.stackPos -= param.size
			param.loc = StackVarLoc(function.stackPos)
		}
		for(local in function.locals) {
			function.stackPos -= local.size
			local.loc = StackVarLoc(function.stackPos)
		}
		function.argsOffset = function.stackPos - function.mostParams * 8
		assembleScope()

		val stackSize = (-function.stackPos).align16()
		byte(0xC9)
		byte(0xC3)
		val endPos = writer.pos
		val prologueSize = 1 + 3 + if(function.stackPos.isImm8) 4 else 7
		writer.pos = startPos - prologueSize
		function.pos.disp = writer.pos
		function.pos.sec = section
		byte(0x50 + 5)
		i24(0xE58948)
		i24(0xEC8348).byte(stackSize)
		writer.pos = endPos
		function.size = writer.pos - function.pos.disp
	}



	private fun assembleBinNode(node: BinNode) {
		if(node.op != BinOp.SET) {
			genExpr(allocReg(true), Types.I64, node)
			return
		}
		val left = node.left.exprSym as? VarNode ?: invalid()
		if(left.type !is IntType) invalid()
		genExpr(Mem.loc(left.loc!!), left.type, node.right)
	}



	private fun genCall(call: CallNode) {
		val function = call.receiver ?: invalid()

		val longDsts = IntArray(call.args.size)

		fun paramType(i: Int): Type = when {
			i < function.params.size -> function.params[i].type
			function.isVararg -> Types.I64
			else -> invalid()
		}

		if(call.hasLongCall) {
			// Generate args with calls of more than 4 arguments
			for((i, arg) in call.args.withIndex()) {
				if(arg.hasLongCall) {
					val stackLoc = allocStack()
					longDsts[i] = stackLoc
					genExpr(Mem.rsp(stackLoc), paramType(i), arg)
				}
			}
			for((i, arg) in call.args.withIndex())
				if(arg.hasLongCall)
					if(i >= 4)
						genMovVarVar(paramType(i), Mem.rsp(32 + (i - 4) * 8), Mem.rsp(longDsts[i]), Reg.RAX)
					else
						genMovRegVar(paramType(i), Reg.arg(i).also(::allocReg), Mem.rsp(longDsts[i]))
		}

		// Generate args beyond the fourth in any order
		for(i in call.args.size - 1 downTo 4) {
			val arg = call.args[i]
			if(arg.hasLongCall) continue
			genExpr(Mem.rbp(function.argsOffset + (i - 4 * 8)), paramType(i), arg)
		}

		// Generate args with calls
		for(i in min(call.args.size - 1, 3) downTo 0) {
			val arg = call.args[i]
			if(!arg.hasCall || arg.hasLongCall) continue
			genExpr(Reg.arg(i).also(::allocReg), paramType(i), arg)
		}

		// Generate args without calls
		for(i in min(call.args.size - 1, 3) downTo 0) {
			val arg = call.args[i]
			if(arg.hasCall) continue
			genExpr(Reg.arg(i).also(::allocReg), paramType(i), arg)
		}

		val receiver = call.left.exprSym as? FunNode ?: invalid()
		if(receiver.isDllImport) {
			byte(0x48)
			byte(0xFF)
			Mem.rip(receiver.pos).write(2)
		} else {
			byte(0xE8)
			context.relRelocs.add(Reloc(SecPos(section, writer.pos), receiver.pos, 0, Width.NONE, Width.DWORD))
			dword(0)
		}

		freeArgRegs()
		val loc = allocStack(8)
		call.loc = StackVarLoc(loc)
		rm(0x88, Reg.ofSize(call.exprSize, 0), Mem.rsp(loc))
	}



	/*
	Allocation
	 */



	private var availableRegs = 0b00001111_00000111

	private fun allocReg(reg: Reg) {
		availableRegs = availableRegs xor (1 shl reg.index)
	}

	private fun allocReg(qword: Boolean): Reg {
		if(availableRegs == 0) error("Spill")
		val reg = availableRegs.countTrailingZeroBits()
		availableRegs = availableRegs xor (1 shl reg)
		return if(qword) Reg.r64(reg) else Reg.r32(reg)
	}

	private fun nextReg(qword: Boolean): Reg {
		if(availableRegs == 0) error("Spill")
		val reg = availableRegs.countTrailingZeroBits()
		return if(qword) Reg.r64(reg) else Reg.r32(reg)
	}

	private fun freeReg(reg: Reg) {
		availableRegs = availableRegs or (1 shl reg.index)
	}

	private fun freeArgRegs() {
		availableRegs = availableRegs or 0b0000_0011_0000_0011
	}

	private fun isAvailable(reg: Reg) = availableRegs and (1 shl reg.index) == 1

	private fun isUsed(reg: Reg) = availableRegs and (1 shl reg.index) == 0



	private val freeStackVars = Stack<Int>()

	private fun allocStack(size: Int = 8): Int {
		if(freeStackVars.isNotEmpty()) return freeStackVars.pop()
		val function = currentFun!!
		val pos = function.stackPos
		function.stackPos -= size
		freeStackVars.push(pos)
		return pos
	}



	/*
	AST pre-generation
	 */



	private fun preGenAddr(node: Node) {
		fun sym(sym: Sym?) {
			if(sym == null || (sym !is FunNode && sym !is VarNode)) invalid()
			node.genType = GenType.SYM
			node.numRegs = 0
		}

		when(node) {
			is ArrayNode -> {
				preGenAddr(node.left)
				if(node.right.isConst) {
					node.numRegs = node.left.numRegs
				} else {
					preGenExprRec(node.right.exprType!!, node.right, null)
					// TODO: Fix this
					node.numRegs = max(node.left.numRegs, node.right.numRegs)
				}
			}

			is NameNode -> sym(node.exprSym)
			is DotNode -> {
				when(node.type) {
					DotNode.Type.SYM -> sym(node.exprSym)
					DotNode.Type.MEMBER -> {
						preGenAddr(node.left)
						node.genType = GenType.MEMBER
						node.numRegs = node.left.numRegs
					}
					DotNode.Type.DEREF -> {
						preGenAddr(node.left)
						node.numRegs = 1
						node.genType = GenType.DEREF
					}
				}
			}
			else -> invalid()
		}
	}



	private fun preGenExprRec(type: Type, node: Node, parentOp: BinOp?) {
		if(node.isConst) {
			if(node.constValue.isImm32) {
				node.genType = GenType.I32
				node.numRegs = 0
			} else {
				node.genType = GenType.I64
				node.numRegs = 1
			}
			return
		}

		when(node) {
			is CallNode -> {
				genCall(node)
				node.genType = GenType.SYM
				node.numRegs = if(type.size <= node.exprSize) 0 else 1
			}
			is NameNode -> {
				node.genType = GenType.SYM
				node.numRegs = if(type.size <= node.exprSize) 0 else 1
			}
			is ArrayNode -> {
				preGenAddr(node)
				node.genType = GenType.ARRAY
				node.numRegs = if(type.size <= node.exprSize) 0 else 1
			}
			is DotNode -> when(node.type) {
				DotNode.Type.SYM -> {
					node.genType = GenType.SYM
					node.numRegs = if(type.size <= node.exprSize) 0 else 1
				}
				DotNode.Type.MEMBER -> {
					preGenAddr(node.left)
					node.genType = GenType.MEMBER
					node.numRegs = if(type.size <= node.exprSize) 0 else 1
				}
				DotNode.Type.DEREF -> {
					preGenAddr(node.left)
					node.genType = GenType.DEREF
					node.numRegs = 1
				}
			}
			is StringNode -> {
				node.genType = GenType.STRING
				node.numRegs = 0
			}
			is UnNode -> {
				preGenExprRec(type, node.child, null)
				if(node.child.isLeaf) {
					node.genType = GenType.UNARY_LEAF
					node.numRegs = 1
				} else {
					node.genType = GenType.UNARY_NODE
					node.numRegs = node.child.numRegs
				}
			}
			is BinNode -> {
				val left = node.left
				val right = node.right
				val op = node.op

				preGenExprRec(type, left, op)
				preGenExprRec(type, right, op)

				if(left.isLeaf) {
					if(right.isLeaf) {
						if(op.isCommutative && (parentOp == op)) {
							node.genType = GenType.BINARY_LEAF_LEAF_COMMUTATIVE
							node.numRegs = 0
						} else {
							node.genType = GenType.BINARY_LEAF_LEAF
							node.numRegs = 1
						}
					} else {
						if(op.isCommutative) {
							node.genType = GenType.BINARY_LEAF_NODE_COMMUTATIVE
							node.numRegs = right.numRegs
						} else {
							node.genType = GenType.BINARY_LEAF_NODE
							node.numRegs = max(2, node.right.numRegs)
						}
					}
				} else if(right.isLeaf) {
					node.genType = GenType.BINARY_NODE_LEAF
					node.numRegs = left.numRegs
				} else {
					if(op.isCommutative && right.numRegs > left.numRegs) {
						node.genType = GenType.BINARY_NODE_NODE_RIGHT
						node.numRegs = right.numRegs
					} else if(right.numRegs == left.numRegs) {
						node.genType = GenType.BINARY_NODE_NODE_LEFT
						node.numRegs = left.numRegs + 1
					} else {
						node.genType = GenType.BINARY_NODE_NODE_LEFT
						node.numRegs = left.numRegs
					}
				}
			}
			else -> err("Invalid node for preGenExpr: $node")
		}
	}



	/*
	AST generation
	 */



	private fun genExpr(dst: Reg, type: Type, node: Node) {
		preGenExprRec(type, node, null)
		if(node.isLeaf)
			genMov(type, dst, node)
		else
			genExprRec(type, dst, node)
	}



	private fun genExpr(dst: Mem, type: Type, node: Node) {
		preGenExprRec(type, node, null)
		if(node.genType == GenType.I32) {
			genMovVarImm(type, dst, node.constValue)
		} else {
			val dst2 = allocReg(type.isQword)
			if(node.isLeaf)
				genMov(type, dst2, node)
			else
				genExprRec(type, dst2, node)
			rm(0x88, dst2.ofSize(type.size), dst)
			freeReg(dst2)
		}
	}



	private fun genExprAddr(dst: Reg, node: Node) {
		when(node.genType) {
			GenType.ARRAY -> {
				node as ArrayNode
				genExprAddr(dst, node.left)
				if(node.right.isConst) {
					val addition = node.right.constValue * node.type!!.size
					if(!addition.isImm32) invalid()
					addrMem.disp += addition.toInt()
				} else {
					val indexReg = allocReg(false)
					genExprRec(Types.I32, indexReg, node.right)
					if(node.type!!.size.is1248) {
						addrMem.sib(dst, indexReg, node.type!!.size, 0)
					} else {
						TODO()
					}
				}
			}
			GenType.SYM -> addrMem.loc(loc(node))
			GenType.MEMBER -> {
				node as DotNode
				genExprAddr(dst, node.left)
				addrMem.disp += node.member!!.offset
			}
			GenType.DEREF -> {
				node as DotNode
				genExprAddr(dst, node.left)
				rm64(0x8B, dst, addrMem)
				addrMem.sib(dst, node.member!!.offset)
			}
			else -> invalid()
		}
	}



	/**
	 * [node] must be a non-leaf node.
	 */
	private fun genExprRec(type: Type, dst: Reg, node: Node) {
		when(node.genType) {
			GenType.NONE -> invalid()
			GenType.I64 -> {
				byte(0x48 or dst.rex)
				byte(0xB0 + dst.value)
				qword(node.constValue)
			}
			GenType.SYM -> {
				genMovRegVar(node.exprType!!, dst, Mem.loc(loc(node)))
			}
			GenType.DEREF -> {
				genExprAddr(dst, node)
				genMovRegVar(node.exprType!!, dst, addrMem)
			}
			GenType.MEMBER -> {
				genExprAddr(dst, node)
				genMovRegVar(node.exprType!!, dst, addrMem)
			}
			GenType.UNARY_LEAF -> {
				node as UnNode
				genMov(type, dst, node.child)
				genUnOp(node.op, dst)
			}
			GenType.UNARY_NODE -> {
				node as UnNode
				genExprRec(type, dst, node.child)
				genUnOp(node.op, dst)
			}
			GenType.BINARY_NODE_NODE_RIGHT -> {
				node as BinNode
				genExprRec(type, dst, node.right)
				val next = allocReg(type.isQword)
				genExprRec(type, next, node.left)
				genBinOpRR(node.op, dst, next)
				freeReg(next)
			}
			GenType.BINARY_NODE_NODE_LEFT -> {
				node as BinNode
				genExprRec(type, dst, node.left)
				val next = allocReg(type.isQword)
				genExprRec(type, next, node.right)
				genBinOpRR(node.op, dst, next)
				freeReg(next)
			}
			GenType.BINARY_NODE_LEAF -> {
				node as BinNode
				genExprRec(type, dst, node.left)
				genBinOp(type, node.op, dst, node.right)
			}
			GenType.BINARY_LEAF_NODE -> {
				node as BinNode
				val next = allocReg(type.isQword)
				genExprRec(type, next, node.right)
				genMov(type, dst, node.left)
				genBinOpRR(node.op, dst, next)
				freeReg(next)
			}
			GenType.BINARY_LEAF_NODE_COMMUTATIVE -> {
				node as BinNode
				genExprRec(type, dst, node.right)
				genBinOp(type, node.op, dst, node.left)
			}
			GenType.BINARY_LEAF_LEAF -> {
				node as BinNode
				if(node.left.isConst) {
					genMov(type, dst, node.right)
					genBinOp(type, node.op, dst, node.left)
				} else {
					genMov(type, dst, node.left)
					genBinOp(type, node.op, dst, node.right)
				}
			}
			else -> err("Invalid GenType: ${node.genType}")
		}
	}



	/**
	 * [src] must be a leaf node.
	 */
	private fun genMov(type: Type, dst: Reg, src: Node) {
		when(src.genType) {
			GenType.I32 -> genMovRegImm(dst, src.constValue)
			GenType.SYM -> rm(0x8A, dst.ofSize(src.exprSize), Mem.loc(loc(src)))
			GenType.MEMBER -> {
				genExprAddr(dst, src)
				rm(0x8A, dst.ofSize(src.exprSize), addrMem)
			}
			GenType.STRING -> rm64(0x8D, dst, Mem.rip((src as StringNode).litSym!!.pos))
			GenType.BINARY_LEAF_LEAF_COMMUTATIVE -> {
				src as BinNode
				genMov(type, dst, src.left)
				genBinOp(type, src.op, dst, src.right)
			}
			else -> err("Invalid GenType ${src.genType} for node $src")
		}
	}

	
	
	/**
	 * [src] must be a leaf node.
	 */
	private fun genBinOp(type: Type, op: BinOp, dst: Reg, src: Node) {
		when(src.genType) {
			GenType.I32 -> genBinOpRI(type, op, dst, src.constValue)
			GenType.SYM -> genBinOpRM(op, dst.ofSize(src.exprSize), loc(src))
			GenType.MEMBER -> {
				genExprAddr(dst, src)
				genBinOpRM(op, dst.ofSize(src.exprSize), loc(src))
			}
			GenType.BINARY_LEAF_LEAF_COMMUTATIVE -> {
				src as BinNode
				genBinOp(type, op, dst, src.left)
				genBinOp(type, op, dst, src.right)
			}
			else -> invalid()
		}
	}



	private fun genUnOp(op: UnOp, dst: Reg) {
		when(op) {
			UnOp.NEG -> r64(0xF6, 2, dst)
			UnOp.NOT -> r64(0xF6, 3, dst)
			else -> TODO()
		}
	}



	/*
	Integer operation generation
	 */



	private fun genBinOpRR(op: BinOp, dst: Reg, src: Reg) {
		when(op) {
			BinOp.ADD -> rr3264(0x03, dst, src)
			BinOp.SUB -> rr3264(0x2B, dst, src)
			BinOp.AND -> rr3264(0x23, dst, src)
			BinOp.OR  -> rr3264(0x0B, dst, src)
			BinOp.XOR -> rr3264(0x33, dst, src)
			BinOp.MUL -> rr3264(0xAF0F, dst, src)
			BinOp.SHL -> genShlRR(4, dst, src)
			BinOp.SHR -> genShlRR(5, dst, src)
			BinOp.DIV -> genDiv(dst) { r3264(0xF7, 7, src) }
			else -> invalid()
		}
	}

	private fun genBinOpRM(op: BinOp, dst: Reg, src: VarLoc) {
		val mem = Mem.loc(src)
		when(op) {
			BinOp.ADD -> rm(0x02, dst, mem)
			BinOp.SUB -> rm(0x2A, dst, mem)
			BinOp.AND -> rm(0x22, dst, mem)
			BinOp.OR  -> rm(0x0A, dst, mem)
			BinOp.XOR -> rm(0x32, dst, mem)
			BinOp.MUL -> when(dst.type) {
				Reg.TYPE_R8 -> {
					if(dst.index == 0) {
						m32(0xF6, 4, mem) // mul byte [src]
					} else {
						byte(0x50) // push rax
						rm32(0x8A, Reg.AL, mem) // mov al, src
						m32(0xF6, 4, mem) // mul byte [src]
						byte(0x58) // pop rax
					}
				}
				Reg.TYPE_R16 -> rm16(0xAF0F, dst, mem)
				Reg.TYPE_R32 -> rm32(0xAF0F, dst, mem)
				Reg.TYPE_R64 -> rm64(0xAF0F, dst, mem)
			}
			BinOp.SHL -> genShlRM(4, dst, mem)
			BinOp.SHR -> genShlRM(5, dst, mem)
			BinOp.DIV -> genDiv(dst) {
				if(dst.isR64)
					word(0x9948) // CQO
				else
					byte(0x99) // CDQ
				m64(0xF7, 7, Mem.loc(src))
			}
			else -> invalid()
		}
	}

	private fun genBinOpRI(type: Type, op: BinOp, dst: Reg, src: Long) {
		when(op) {
			BinOp.ADD -> genGroup1RI(0, dst, src)
			BinOp.OR  -> genGroup1RI(1, dst, src)
			BinOp.AND -> genGroup1RI(4, dst, src)
			BinOp.SUB -> genGroup1RI(5, dst, src)
			BinOp.XOR -> genGroup1RI(6, dst, src)
			BinOp.SHL -> genGroup2RI(4, dst, src)
			BinOp.SHR -> genGroup2RI(5, dst, src)
			BinOp.MUL -> {
				if(src.absoluteValue.countOneBits() == 1) {
					if(src < 0) r3264(0xF7, 3, dst) // neg dst
					r3264(0xC1, 4, dst) // shl
					byte(src.countTrailingZeroBits())
				} else {
					genImulRI(dst, src)
				}
			}
			BinOp.DIV -> {
				// Substitute with SHR if not signed
				if(!type.signed && src.countOneBits() == 1) {
					r3264(0xC1, 5, dst) // shl
					byte(src.countTrailingZeroBits())
					return
				}

				genDiv(dst) {
					val srcReg = nextReg(type.isQword)
					genMovRegImm(srcReg, src)
					when {
						src > 0 -> rr32(0x33, Reg.EDX, Reg.EDX) // xor edx, edx
						dst.isR64 -> word(0x9948) // CQO
						else -> byte(0x99) // CDQ
					}
					r3264(0xF7, 7, srcReg)
				}
			}
			else -> invalid()
		}
	}

	private fun genGroup1RI(ext: Int, dst: Reg, src: Long) {
		if(dst.isR64) byte(0b0100_1000 or dst.rexB)
		else if(dst.hasRex) byte(0b0100_0100)
		if(src.isImm8) {
			byte(0x83)
			byte(0b11_000_000 or (ext shl 3) or dst.rmValue)
			byte(src.toInt())
		} else {
			byte(0x81)
			byte(0b11_000_000 or (ext shl 3) or dst.rmValue)
			dword(src.toInt())
		}
	}

	private fun genImulRI(dst: Reg, src: Long) {
		if(dst.isR64) byte(0b0100_1000 or dst.rexR or dst.rexB)
		else if(dst.hasRex) byte(0b0100_0101)
		if(src.isImm8) {
			byte(0x6B)
			byte(0b11_000_000 or dst.regValue or dst.rmValue)
			byte(src.toInt())
		} else {
			byte(0x69)
			byte(0b11_000_000 or dst.regValue or dst.rmValue)
			dword(src.toInt())
		}
	}

	private fun genGroup2RI(ext: Int, dst: Reg, src: Long) {
		r3264(0xC0, ext, dst)
		byte(src.toInt())
	}

	private fun genDiv(dst: Reg, block: () -> Unit) {
		if(isUsed(Reg.RDX)) byte(0x52) // PUSH RDX
		if(dst.index != 0) {
			byte(0x50) // PUSH RAX
			rr64(0x8B, Reg.RAX, dst) // MOV RAX, DST
		}
		block() // IDIV SRC
		if(dst.index != 0) {
			rr64(0x8B, dst, Reg.RAX) // MOV DST, RAX
			byte(0x58) // POP RAX
		}
		if(isUsed(Reg.RDX)) byte(0x5A) // POP RDX
	}

	private fun genShlRM(ext: Int, dst: Reg, src: Mem) {
		if(isUsed(Reg.RCX)) byte(0x51) // push rcx
		rm32(0x8A, Reg.CL, src) // mov cl, [src]
		r3264(0xD3, ext, dst) // shl dst, cl
		if(isUsed(Reg.RCX)) byte(0x59) // pop rcx
	}

	private fun genShlRR(ext: Int, dst: Reg, src: Reg) {
		if(src == Reg.RCX) {
			r3264(0xD3, ext, dst) // shl dst, cl
		} else {
			if(isUsed(Reg.RCX)) byte(0x51) // push rcx
			rr32(0x8B, Reg.ECX, src) // mov ecx, src
			r3264(0xD3, ext, dst) // shl dst, cl
			if(isUsed(Reg.RCX)) byte(0x59) // pop rcx
		}
	}



	/*
	Codegen utils
	 */



	private fun Any.byte(value: Int) = writer.i8(value)

	private fun Any.word(value: Int) = writer.i16(value)

	private fun Any.i24(value: Int) = writer.i24(value)

	private fun Any.dword(value: Int) = writer.i32(value)

	private fun Any.qword(value: Long) = writer.i64(value)



	private fun Mem.write(reg: Int) {
		when(type) {
			Mem.Type.RIP -> {
				byte((reg shl 3) or 0b101)
				context.relRelocs.add(Reloc(SecPos(section, writer.pos), reloc!!, disp, immWidth, Width.DWORD))
				dword(0)
			}
			Mem.Type.RBP -> {
				if(disp.isImm8)
					byte(0b01_000_101 or (reg shl 3)).byte(disp)
				else
					byte(0b10_000_101 or (reg shl 3)).dword(disp)
			}
			Mem.Type.RSP -> {
				if(disp.isImm8)
					byte(0b01_000_100 or (reg shl 3)).byte(0x24).byte(disp)
				else
					byte(0b10_000_100 or (reg shl 3)).byte(0x24).dword(disp)
			}
			Mem.Type.SIB -> {
				val i = index.value
				val b = base.value

				val mod = if(disp == 0) 0 else if(disp.isImm8) 1 else 2
				fun disp() = if(disp == 0) Unit else if(disp.isImm8) byte(disp) else dword(disp)

				if(index.isValid) { // SIB: [R*S] or [R*S+DISP] or [R+R*S] or [R+R*S+DISP]
					if(index.isInvalidSibIndex) invalid()
					val s = when(scale) { 1 -> 0 2 -> 1 4 -> 2 8 -> 4 else -> err("Invalid scale") }
					if(base.isValid) {
						if(b == 5 && disp == 0) { // [RBP/R13+R*S] -> [RBP+R*S+DISP8]
							i24(0b01_000_100 or (reg shl 3) or (s shl 14) or (i shl 11) or (0b101 shl 8))
						} else {
							word((mod shl 6) or (reg shl 3) or 0b100 or (s shl 14) or (i shl 11) or (b shl 8))
							disp()
						}
					} else { // Index only, requires disp32
						word((reg shl 3) or 0b100 or (s shl 14) or (i shl 11) or (0b101 shl 8))
						dword(disp)
					}
				} else if(base.isValid) { // Indirect: [R] or [R+DISP]
					if(b == 4) { // [RSP/R12] -> [RSP/R12+NONE*1] (same with DISP)
						word((mod shl 6) or (reg shl 3) or 0b100 or (0b00_100_100 shl 8))
						disp()
					} else if(b == 5 && disp == 0) { // [RBP/R13] -> [RBP/R13+0]
						word(0b00000000_01_000_101 or (reg shl 3))
					} else {
						byte((mod shl 6) or (reg shl 3) or b)
						disp()
					}
				} else {
					invalid()
				}
			}
		}
	}




	private fun m16(opcode: Int, reg: Int, rm: Mem) {
		byte(0x66)
		writer.varLengthInt(opcode)
		rm.write(reg)
	}

	private fun m32(opcode: Int, reg: Int, rm: Mem) {
		writer.varLengthInt(opcode)
		rm.write(reg)
	}

	private fun m64(opcode: Int, reg: Int, rm: Mem) {
		byte(0x48)
		writer.varLengthInt(opcode)
		rm.write(reg)
	}

	private fun r64(opcode: Int, reg: Int, rm: Reg) {
		byte(0b0100_1000 or rm.rexB)
		writer.varLengthInt(opcode)
		byte(0b11_000_000 or (reg shl 3) or rm.rmValue)
	}

	private fun rr64(opcode: Int, reg: Reg, rm: Reg) {
		byte(0b0100_1000 or reg.rexR or rm.rexB)
		writer.varLengthInt(opcode)
		byte(0b11_000_000 or reg.regValue or rm.rmValue)
	}

	private fun rr32(opcode: Int, reg: Reg, rm: Reg) {
		val rex = reg.rexR or rm.rexB
		if(rex != 0) byte(0x40 or rex)
		writer.varLengthInt(opcode)
		byte(0b11_000_000 or reg.regValue or rm.rmValue)
	}

	private fun rm8(opcode: Int, reg: Reg, mem: Mem) {
		if(reg.hasRex) byte(0x41) else if(reg.requiresRex) byte(0x40)
		writer.varLengthInt(opcode)
		mem.write(reg.value)
	}

	private fun rm16(opcode: Int, reg: Reg, mem: Mem) {
		byte(0x66)
		if(reg.hasRex) byte(0x41)
		writer.varLengthInt(opcode)
		mem.write(reg.value)
	}

	private fun rm32(opcode: Int, reg: Reg, mem: Mem) {
		if(reg.hasRex) byte(0x41)
		writer.varLengthInt(opcode)
		mem.write(reg.value)
	}

	private fun rm64(opcode: Int, reg: Reg, mem: Mem) {
		byte(0x48 or reg.rexR)
		writer.varLengthInt(opcode)
		mem.write(reg.value)
	}

	private fun rm(opcode: Int, reg: Reg, mem: Mem) {
		when(reg.type) {
			Reg.TYPE_R8  -> rm8(opcode, reg, mem)
			Reg.TYPE_R16 -> rm16(opcode + 1, reg, mem)
			Reg.TYPE_R32 -> rm32(opcode + 1, reg, mem)
			Reg.TYPE_R64 -> rm64(opcode + 1, reg, mem)
			else         -> invalid()
		}
	}

	private fun rr3264(opcode: Int, reg: Reg, rm: Reg) {
		if(reg.isR64) rr64(opcode, reg, rm) else rr32(opcode, reg, rm)
	}

	private fun rm3264(opcode: Int, dst: Reg, src: Mem) {
		if(dst.isR64) byte(0x48 or dst.rexB) else if(dst.hasRex) byte(0x41)
		writer.varLengthInt(opcode)
		src.write(dst.value)
	}

	private fun r3264(opcode: Int, ext: Int, rm: Reg) {
		if(rm.isR64) byte(0x48 or rm.rexR) else if(rm.hasRex) byte(0x41)
		writer.varLengthInt(opcode)
		byte(0b11_000_000 or (ext shl 3) or rm.rmValue)
	}




	/*
	Complex moves
	 */



	private fun genMovRegVar(srcType: Type, dst: Reg, src: Mem) {
		// 1: movsx/movzx rcx, byte [src]
		// 2: movsx/movzx rcx, word [src]
		// 4s: movsxd rcx, [src]
		// 4u: mov ecx, [src]
		// 8: mov rcx, [src]
		when(srcType.size) {
			1 -> rm3264(if(srcType.signed) 0xBE0F else 0xB60F, dst, src)
			2 -> rm3264(if(srcType.signed) 0xBF0F else 0xB70F, dst, src)
			4 -> if(dst.isR32 || srcType.unsigned)
					rm32(0x8B, dst, src)
				else
					rm64(0x63, dst, src)
			8 -> rm64(0x8B, dst, src)
			else -> err("Invalid type size")
		}
	}



	private fun genMovVarVar(type: Type, dst: Mem, src: Mem, intermediary: Reg) {
		when(type.size) {
			1 -> {
				rm32(0xB60F, intermediary, src) // movzx intermediary, byte [src]
				rm8(0x88, intermediary, dst) // mov byte [dst], intermediary
			}
			2 -> {
				rm32(0xB70F, intermediary, src) // movzx intermediary, word [src]
				rm16(0x89, intermediary, dst) // mov word [dst], intermediary
			}
			4 -> {
				rm32(0x89, intermediary, src) // mov intermediary, dword [src]
				rm32(0x8B, intermediary, src) // mov dword [dst], intermediary
			}
			8 -> {
				rm64(0x89, intermediary, src) // mov intermediary, qword [src]
				rm32(0x8B, intermediary, src) // mov qword [dst], intermediary
			}
			else -> invalid()
		}
	}



	private fun genMovVarImm(type: Type, dst: Mem, src: Long) {
		// 1:   mov byte [rcx], 10
		// 2:   mov word [rcx], 10
		// 4:   mov dword [rcx], 10
		// 8u:  mov dword [rcx], 10
		// 8s:  mov qword [rcx], 10
		// I64: mov rax, qword src; mov qword [rcx], rax
		when(type.size) {
			1 -> m32(0xC6, 0, dst.also { it.immWidth = Width.BYTE }).byte(src.toInt())
			2 -> m16(0xC7, 0, dst.also { it.immWidth = Width.WORD }).word(src.toInt())
			4 -> m32(0xC7, 0, dst.also { it.immWidth = Width.DWORD }).dword(src.toInt())
			8 -> if(src.isImm32) {
				if(type.signed)
					m64(0xC7, 0, dst.also { it.immWidth = Width.DWORD }).dword(src.toInt())
				else
					m32(0xC7, 0,dst.also { it.immWidth = Width.DWORD }).dword(src.toInt())
			} else {
				word(0xB048).qword(src) // mov rax, qword src
				m32(0x89, 0, dst) // mov [dst], rax
			}
		}
	}



	private fun genMovRegImm(dst: Reg, src: Long) {
		// imm64      mov rax, qword src  (RW B8 MOV O64_I64)
		// pos imm32  mov eax, dword src  (C7/0 MOV RM32_I32) (not sign-extended)
		// neg imm32  mov rax, dword src  (RW C7/0 MOV RM64_I32) (sign-extended)
		// 0          xor eax, eax        (31 XOR R32_R32)
		when {
			src.isImm64 -> word(0xB848 or dst.rex or (dst.value shl 8)).qword(src)
			src > 0     -> rr32(0xC7, Reg.NONE, dst).dword(src.toInt())
			src < 0     -> rr64(0xC7, Reg.NONE, dst).dword(src.toInt())
			else        -> rr32(0x31, dst, dst)
		}
	}





}