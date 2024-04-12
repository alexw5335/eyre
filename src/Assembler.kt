package eyre

import kotlin.math.absoluteValue
import kotlin.math.max

class Assembler(private val context: Context) {


	private lateinit var file: SrcFile

	private var writer = context.textWriter

	private var section = context.textSec

	private var nodeIndex = 0

	private var currentFun: FunNode? = null

	private var currentSrcPos: SrcPos? = null

	private fun invalid(): Nothing = throw EyreError(currentSrcPos, "Invalid encoding")

	private fun err(message: String = "No reason given"): Nothing = throw EyreError(currentSrcPos, message)



	/*
	Assembly
	 */



	fun assembleFile(file: SrcFile) {
		this.nodeIndex = 0
		this.file = file

		try {
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
				is VarNode       -> { }
				is CallNode      -> genCall(node)
				is StructNode    -> { }
				is BinNode       -> assembleBinNode(node)
				else             -> err("Invalid node: $node")
			}
		}
	}



	private fun assembleFunction(function: FunNode) {
		currentFun = function

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
	}



	private fun allocateStack(size: Int): Int {
		val function = currentFun!!
		val disp = function.stackPos
		function.stackPos -= size
		return disp
	}



	private fun assembleBinNode(node: BinNode) {
		if(node.op != BinOp.SET) invalid()
		val left = node.left.exprSym as? VarNode ?: invalid()
		if(left.type !is IntType) invalid()
		fullGenExpr(left.loc!!, left.type, node.right)
	}



	private fun genMovRegToVar(type: Type, dst: VarLoc, src: Reg) {
		if(dst is StackVarLoc) {
			when(type.size) {
				1 -> rm8(0x88, src.asR8, dst)
				2 -> rm16(0x89, src, dst)
				4 -> rm32(0x89, src, dst)
				8 -> rm64(0x89, src, dst)
			}
		} else {
			invalid()
		}
	}



	private fun genCall(call: CallNode, shouldStore: Boolean) {
		val function = call.receiver ?: invalid()
		if(call.args.size != function.params.size) invalid()
		var lastCall = -1
		for(argIndex in call.args.size - 1 downTo 0) {
			val arg = call.args[argIndex]
			val param = function.params[argIndex]
			val argType = arg.exprType ?: invalid()
			val type = param.type
			if(!checkTypes(type, argType)) invalid()
			if(argIndex >= 4) {
				fullGenExpr(StackVarLoc(function.argsOffset + (argIndex - 4 * 8)), type, arg)
			} else {
				if(hasCall(arg)) {
					arg.hasCall = true
					lastCall = argIndex
				}
			}
		}

		for(i in 3 downTo 0) {
			val arg = call.args[i]
			if(arg.hasCall) {
				val dst = Reg.arg(i)
				allocReg(dst)
				fullGenExpr(dst, type, arg)
				if(lastCall == i) {

				} else {

				}
			}
		}

		for(i in 3 downTo 0) {
			if(!exprArgs[i]) continue
			val dst = Reg.arg(i)
			allocReg(dst)
			genExprRec(function.params[i].type, dst, call.args[i])
		}

		for(i in 3 downTo 0) {
			if(!constArgs[i]) continue
			val dst = Reg.arg(i)
			allocReg(dst)
			genMovRegImm(dst, call.args[i].constValue)
		}

		freeArgRegs()

		val loc = StackVarLoc(allocateStack(8))
		call.loc = loc
		genMovRegToVar(call.exprType!!, loc, Reg.RAX)
	}



	private fun checkTypes(a: Type, b: Type): Boolean = when(a) {
		is PointerType -> b is PointerType && checkTypes(a.type, b.type)
		is IntType     -> b is IntType
		else           -> a == b
	}



	/*
	Register allocation
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



	/*
	AST pre-generation
	 */



	private fun hasCall(node: Node): Boolean = when(node) {
		is CallNode -> true
		is UnNode   -> hasCall(node.child)
		is BinNode  -> hasCall(node.left) || hasCall(node.right)
		else        -> false
	}



	private fun symGenType(type: Type, node: Node) {
		val symType = node.exprType as? IntType ?: invalid()
		node.genType = when(symType.size) {
			1 -> when {
				type.size == 1 -> GenType.SYM8
				symType.signed -> GenType.SYM_I8
				else -> GenType.SYM_U8
			}
			2 -> when {
				type.size <= 2 -> GenType.SYM16
				symType.signed -> GenType.SYM_I16
				else -> GenType.SYM_U16
			}
			4 -> when {
				type.size <= 4 -> GenType.SYM32
				symType.signed -> GenType.SYM_I32
				else           -> GenType.SYM_U32
			}
			8 -> GenType.SYM64
			else -> invalid()
		}
		node.isLeaf = node.genType.isLeaf
		node.numRegs = if(node.isLeaf) 1 else 0
	}



	private fun preGenExpr(type: Type, node: Node) = preGenExprRec(type, node, null)



	private fun preGenExprRec(type: Type, node: Node, parentOp: BinOp?) {
		if(node.isConst) {
			node.genType = when {
				node.constValue < Byte.MAX_VALUE -> GenType.I8
				node.constValue < Int.MAX_VALUE -> GenType.I32
				else -> GenType.I64
			}
			node.numRegs = if(node.genType.isLeaf) 0 else 1
		}

		when(node) {
			is IntNode -> {
				node.genType = GenType.I32
			}
			is CallNode -> {
				genCall(node)
				symGenType(type, node)
			}
			is NameNode -> symGenType(type, node)
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
							node.isLeaf = true
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



	private fun fullGenExpr(dst: Reg, type: Type, node: Node) {
		preGenExpr(type, node)
		if(node.isLeaf)
			genMov(type, dst, node)
		else
			genExprRec(type, dst, node)
	}

	private fun fullGenExpr(dst: VarLoc, type: Type, node: Node) {
		preGenExpr(type, node)
		if(node.genType.isImm) {
			genMovVarImm(type, dst, node.constValue)
		} else {
			val dst2 = allocReg(type.isQword)
			if(node.isLeaf)
				genMov(type, dst2, node)
			else
				genExprRec(type, dst2, node)
			genMovRegToVar(type, dst, dst2)
			freeReg(dst2)
		}
	}

	private fun genExpr(type: Type, node: Node): Reg {
		val dst = allocReg(type.isQword)
		if(node.isLeaf)
			genMov(type, dst, node)
		else
			genExprRec(type, dst, node)
		freeReg(dst)
		return dst
	}



	/**
	 * [node] must be a non-leaf node.
	 */
	private fun genExprRec(type: Type, dst: Reg, node: Node) {
		when(node.genType) {
			GenType.NONE    -> invalid()
			GenType.I64     -> TODO()
			GenType.SYM_U8  -> rm3264(0xB60F, dst, (node.exprSym as VarNode).loc!!) // movzx dst3264, byte [src]
			GenType.SYM_I8  -> rm3264(0xBE0F, dst, (node.exprSym as VarNode).loc!!) // movsx dst3264, byte [src]
			GenType.SYM_U16 -> rm3264(0xB70F, dst, (node.exprSym as VarNode).loc!!) // movzx dst3264, word [src]
			GenType.SYM_I16 -> rm3264(0xBF0F, dst, (node.exprSym as VarNode).loc!!) // movsx dst3264, word [src]
			GenType.SYM_U32 -> rm32(0x8B, dst, (node.exprSym as VarNode).loc!!) // mov dst3264, [src]
			GenType.SYM_I32 -> rm64(0x63, dst, (node.exprSym as VarNode).loc!!) // movsxd dst64, [src]
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
				genMov(type, dst, node.left)
				genBinOp(type, node.op, dst, node.right)
			}
			GenType.BINARY_LEAF_LEAF_COMMUTATIVE -> {
				node as BinNode
				genBinOp(type, node.op, dst, node.left)
				genBinOp(type, node.op, dst, node.right)
			}
			else -> err("Invalid GenType: ${node.genType}")
		}
	}



	private fun genUnOp(op: UnOp, dst: Reg) {
		when(op) {
			UnOp.NEG -> r64(0xF6, 2, dst)
			UnOp.NOT -> r64(0xF6, 3, dst)
			else -> TODO()
		}
	}



	private fun loc(node: Node): VarLoc = if(node is CallNode)
		node.loc!!
	else
		(node.exprSym as VarNode).loc!!



	/**
	 * [src] must be a leaf node.
	 */
	private fun genMov(type: Type, dst: Reg, src: Node) {
		when(src.genType) {
			GenType.I8,
			GenType.I32 -> genMovRegImm(dst, src.constValue)
			GenType.SYM8 -> rm8(0x8A, dst, loc(src))
			GenType.SYM16 -> rm16(0x8B, dst, loc(src))
			GenType.SYM32 -> rm32(0x8B, dst, loc(src))
			GenType.SYM64 -> rm64(0x8B, dst, loc(src))
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
			GenType.I8,
			GenType.I32 -> genBinOpRI(type, op, dst, src.constValue)
			GenType.SYM8 -> genBinOpRM(op, dst.asR8, loc(src))
			GenType.SYM16 -> genBinOpRM(op, dst.asR16, loc(src))
			GenType.SYM32 -> genBinOpRM(op, dst.asR32, loc(src))
			GenType.SYM64 -> genBinOpRM(op, dst.asR64, loc(src))
			GenType.BINARY_LEAF_LEAF_COMMUTATIVE -> {
				src as BinNode
				genBinOp(type, op, dst, src.left)
				genBinOp(type, op, dst, src.right)
			}
			else -> invalid()
		}
	}

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
		when(op) {
			BinOp.ADD -> rm(0x02, dst, src)
			BinOp.SUB -> rm(0x2A, dst, src)
			BinOp.AND -> rm(0x22, dst, src)
			BinOp.OR  -> rm(0x0A, dst, src)
			BinOp.XOR -> rm(0x32, dst, src)
			BinOp.MUL -> when(dst.type) {
				Reg.TYPE_R8  -> {
					if(dst.index == 0) {
						m32(0xF6, 4, src) // mul byte [src]
					} else {
						byte(0x50) // push rax
						rm32(0x8A, Reg.AL, src) // mov al, src
						m32(0xF6, 4, src) // mul byte [src]
						byte(0x58) // pop rax
					}
				}
				Reg.TYPE_R16 -> rm16(0xAF0F, dst, src)
				Reg.TYPE_R32 -> rm32(0xAF0F, dst, src)
				Reg.TYPE_R64 -> rm64(0xAF0F, dst, src)
			}
			BinOp.SHL -> genShlRM(4, dst, src)
			BinOp.SHR -> genShlRM(5, dst, src)
			BinOp.DIV -> genDiv(dst) {
				if(dst.isR64)
					word(0x9948) // CQO
				else
					byte(0x99) // CDQ
				m64(0xF7, 7, src)
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

	private fun genShlRM(ext: Int, dst: Reg, src: VarLoc) {
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



	private fun writeVarMem(mem: VarLoc, reg: Int, immWidth: Width) {
		if(mem is GlobalVarLoc) {
			byte((reg shl 3) or 0b101)
			context.relRelocs.add(Reloc(SecPos(section, writer.pos), mem.reloc, 0, immWidth, Width.DWORD))
			dword(0)
			return
		} else if(mem is StackVarLoc) {
			if(mem.disp.isImm8)
			byte(0b01_000_101 or (reg shl 3)).byte(mem.disp)
			else
			byte(0b10_000_101 or (reg shl 3)).dword(mem.disp)
		} else {
			invalid()
		}
	}



	private fun m32(opcode: Int, reg: Int, rm: VarLoc, immWidth: Width = Width.NONE) {
		writer.varLengthInt(opcode)
		writeVarMem(rm, reg, immWidth)
	}

	private fun m64(opcode: Int, reg: Int, rm: VarLoc, immWidth: Width = Width.NONE) {
		byte(0x48)
		writer.varLengthInt(opcode)
		writeVarMem(rm, reg, immWidth)
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

	private fun rm8(opcode: Int, reg: Reg, mem: VarLoc) {
		if(reg.hasRex) byte(0x41) else if(reg.requiresRex) byte(0x40)
		writer.varLengthInt(opcode)
		writeVarMem(mem, reg.value, Width.NONE)
	}

	private fun rm16(opcode: Int, reg: Reg, mem: VarLoc) {
		byte(0x66)
		if(reg.hasRex) byte(0x41)
		writer.varLengthInt(opcode)
		writeVarMem(mem, reg.value, Width.NONE)
	}

	private fun rm32(opcode: Int, reg: Reg, mem: VarLoc) {
		if(reg.hasRex) byte(0x41)
		writer.varLengthInt(opcode)
		writeVarMem(mem, reg.value, Width.NONE)
	}

	private fun rm64(opcode: Int, reg: Reg, mem: VarLoc) {
		byte(0x48 or reg.rexR)
		writer.varLengthInt(opcode)
		writeVarMem(mem, reg.value, Width.NONE)
	}

	private fun rm(opcode: Int, reg: Reg, mem: VarLoc) {
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

	private fun rm3264(opcode: Int, dst: Reg, src: VarLoc) {
		if(dst.isR64) byte(0x48 or dst.rexB) else if(dst.hasRex) byte(0x41)
		writer.varLengthInt(opcode)
		writeVarMem(src, dst.value, Width.NONE)
	}

	private fun r3264(opcode: Int, ext: Int, rm: Reg) {
		if(rm.isR64) byte(0x48 or rm.rexR) else if(rm.hasRex) byte(0x41)
		writer.varLengthInt(opcode)
		byte(0b11_000_000 or (ext shl 3) or rm.rmValue)
	}




	/*
	Complex moves
	 */



	private fun genMovVarImm(type: Type, dst: VarLoc, src: Long) {
		// 1:   mov byte [rcx], 10
		// 2:   mov word [rcx], 10
		// 4:   mov dword [rcx], 10
		// 8u:  mov dword [rcx], 10
		// 8s:  mov qword [rcx], 10
		// I64: mov rax, qword src; mov qword [rcx], rax
		when(type.size) {
			1 -> {
				byte(0xC6)
				writeVarMem(dst, 0, Width.BYTE)
				if(src !in Width.BYTE) invalid()
				byte(src.toInt())
			}
			2 -> {
				word(0xC766)
				writeVarMem(dst, 0, Width.WORD)
				if(src !in Width.WORD) invalid()
				word(src.toInt())
			}
			4 -> {
				byte(0xC7)
				writeVarMem(dst, 0, Width.DWORD)
				if(src !in Width.DWORD) invalid()
				dword(src.toInt())
			}
			8 -> if(src.isImm32) {
				if(type.signed) {
					word(0xC748)
					writeVarMem(dst, 0, Width.DWORD)
					dword(src.toInt())
				} else {
					byte(0xC7)
					writeVarMem(dst, 0, Width.BYTE)
					dword(src.toInt())
				}
			} else {
				word(0xB048) // mov rax, qword src
				qword(src)
				byte(0x89) // mov [dst], rax
				writeVarMem(dst, 0, Width.NONE)
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