package eyre

import kotlin.math.max
import kotlin.random.Random

class Assembler(private val context: Context) {


	private lateinit var file: SrcFile

	private var writer = context.textWriter

	private var section = context.textSec

	private var nodeIndex = 0

	private var currentFun: FunNode? = null

	private var currentSrcPos: SrcPos? = null

	private fun invalid(): Nothing = throw EyreError(currentSrcPos, "Invalid encoding")

	private fun err(message: String = "No reason given"): Nothing = throw EyreError(currentSrcPos, message)

	private val instructions = ArrayList<Instruction>()

	private fun ins(mnemonic: Mnemonic, op1: Operand? = null, op2: Operand? = null, op3: Operand? = null) =
		instructions.add(Instruction(mnemonic, op1, op2, op3))



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
				is CallNode      -> { }
				is StructNode    -> { }
				is BinNode       -> assembleBinNode(node)
				else             -> err("Invalid node: $node")
			}
		}
	}



	/*
	Code generation
	 */



	private fun VarNode.width() = when(size) {
		1    -> Width.BYTE
		2    -> Width.WORD
		4    -> Width.DWORD
		8    -> Width.QWORD
		else -> Width.NONE
	}

	private fun assembleFunction(function: FunNode) {
		currentFun = function

		function.stackPos = -32
		for(param in function.params) {
			function.stackPos -= param.size
			param.mem = StackOperand(function.stackPos, param.width())
		}
		for(local in function.locals) {
			function.stackPos -= local.size
			local.mem = StackOperand(function.stackPos, local.width())
		}

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
		val left = node.left.exprSym ?: invalid()
		if(left !is VarNode) invalid()
		if(left.type !is IntType) invalid()
		genExpr(left.type, node.right)
		instructions.forEach { println(it.printString) }
	}



	private fun genCall(call: CallNode) {
		val function = call.receiver ?: invalid()
		if(call.args.size != function.params.size) invalid()
		for((argIndex, arg) in call.args.withIndex()) {
			val param = function.params[argIndex]
			val argType = arg.exprType ?: invalid()
			if(!checkTypes(param.type, argType)) invalid()
		}
	}

	private fun checkTypes(a: Type, b: Type): Boolean = when(a) {
		is PointerType -> b is PointerType && checkTypes(a.type, b.type)
		is IntType     -> b is IntType
		else           -> a == b
	}



	/*
	AST generation
	 */



	private var availableRegs = 0b00001111_00000111

	private fun allocReg(reg: Reg) {
		availableRegs = availableRegs xor (1 shl reg.index)
	}

	private fun allocReg(): Reg {
		if(availableRegs == 0) error("Spill")
		val reg = availableRegs.countTrailingZeroBits()
		availableRegs = availableRegs xor (1 shl reg)
		return Reg.r64(reg)
	}

	private fun nextReg(): Reg {
		if(availableRegs == 0) error("Spill")
		val reg = availableRegs.countTrailingZeroBits()
		return Reg.r64(reg)
	}

	private fun freeReg(reg: Reg) {
		availableRegs = availableRegs or (1 shl reg.index)
	}

	private fun isAvailable(reg: Reg) = availableRegs and (1 shl reg.index) == 1

	private fun isUsed(reg: Reg) = availableRegs and (1 shl reg.index) == 0


	private fun genExpr(type: Type, node: Node): Reg {
		instructions.clear()
		val dst = allocReg()
		preGenExpr(type, node, null)
		genExprRec(type, dst, node)
		freeReg(dst)
		return dst
	}

	private fun preGenExpr(type: Type, node: Node, parentOp: BinOp?) {
		fun sym(sym: Sym?) {
			when(sym) {
			is VarNode -> {
				if(sym.type !is IntType) invalid()
				when(sym.type.size) {
					1 -> node.isLeaf = type.size == 1
					2 -> node.isLeaf = type.size <= 2
					4 -> node.isLeaf = type.size <= 4
					8 -> node.isLeaf = true
				}
				node.numRegs = if(node.isLeaf) 1 else 0
			}
			else -> invalid()
		} }
		when(node) {
			is IntNode -> node.isLeaf = true
			is NameNode -> sym(node.exprSym)
			is UnNode -> {
				preGenExpr(type, node.child, null)
				if(node.child.isLeaf) {
					node.numRegs = 1
				} else {
					node.numRegs = node.child.numRegs
					node.requiredRegs = node.child.requiredRegs
				}
			}
			is BinNode -> {
				val left = node.left
				val right = node.right
				val op = node.op

				preGenExpr(type, left, op)
				preGenExpr(type, right, op)

				if(left.isLeaf) {
					if(right.isLeaf) {
						if(op.isCommutative && (parentOp == op)) {
							node.isLeaf = true
							node.numRegs = 0
						} else {
							node.numRegs = 1
						}
					} else {
						node.numRegs = if(op.isCommutative) right.numRegs else max(2, node.right.numRegs)
					}
				} else if(right.isLeaf) {
					node.numRegs = left.numRegs
				} else {
					node.numRegs = if(left.numRegs != right.numRegs)
						max(left.numRegs, right.numRegs)
					else
						left.numRegs + 1
				}
			}
			else -> invalid()
		}
	}



	private fun genExprRec(type: Type, dst: Reg, node: Node) {
		when(node) {
			is NameNode -> genMovRegVar(dst, node.exprSym as? VarNode ?: invalid())
			is UnNode -> {
				val child = node.child
				val op = node.op
				if(child.isLeaf) {
					genMov(type, dst, child)
					genUnOp(op, dst)
				} else {
					genExprRec(type, dst, child)
					genUnOp(op, dst)
				}
			}

			is BinNode -> {
				val left = node.left
				val right = node.right
				val op = node.op

				if(left.isLeaf) {
					if(right.isLeaf) {
						genMov(type, dst, node)
					} else {
						if(op.isCommutative) {
							genExprRec(type, dst, right)
							genBinOp(op, dst, left)
						} else {
							val next = allocReg()
							genExprRec(type, next, right)
							genMov(type, dst, left)
							genBinOpRR(op, dst, next)
							freeReg(next)
						}
					}
				} else if(right.isLeaf) {
					genExprRec(type, dst, left)
					genBinOp(op, dst, right)
				} else {
					if(op.isCommutative && right.numRegs > left.numRegs) {
						genExprRec(type, dst, right)
						val next = allocReg()
						genExprRec(type, next, left)
						genBinOpRR(op, dst, next)
						freeReg(next)
					} else {
						genExprRec(type, dst, left)
						val next = allocReg()
						genExprRec(type, next, right)
						genBinOpRR(op, dst, next)
						freeReg(next)
					}
				}
			}
			else -> TODO()
		}
	}



	private fun genUnOp(op: UnOp, dst: Reg) {
		when(op) {
			UnOp.NEG -> r64(0xF6, 2, dst)
			UnOp.NOT -> r64(0xF6, 3, dst)
			else -> TODO()
		}
	}



	private fun genShlRM(ext: Int, dst: Reg, src: Operand) {
		if(isUsed(Reg.RCX)) byte(0x51) // push rcx
		rm64(0x8B, Reg.RCX, src) // mov rcx, [src]
		r64(0xD3, ext, dst) // shl dst, cl
		if(isUsed(Reg.RCX)) byte(0x59) // pop rcx
	}



	private fun genShlRR(ext: Int, dst: Reg, src: Reg) {
		if(src == Reg.RCX) {
			r64(0xD3, ext, dst) // shl dst, cl
		} else {
			if(isUsed(Reg.RCX)) byte(0x51) // push rcx
			rr64(0x8B, Reg.RCX, src) // mov rcx, src
			r64(0xD3, ext, dst)// shl dst, cl
			if(isUsed(Reg.RCX)) byte(0x59) // pop rcx
		}
	}



	private fun genDiv(dst: Reg, block: () -> Unit) {
		if(isUsed(Reg.RDX)) byte(0x52) // PUSH RDX
		if(dst != Reg.RAX) {
			byte(0x50) // PUSH RAX
			rr64(0x8B, Reg.RAX, dst) // MOV RAX, DST
		}
		word(0x9948) // CQO
		block() // IDIV SRC
		if(dst != Reg.RAX) {
			rr64(0x8B, dst, Reg.RAX) // MOV DST, RAX
			byte(0x58) // POP RAX
		}
		if(isUsed(Reg.RDX)) byte(0x5A) // POP RDX
	}



	private fun genBinOpRR(op: BinOp, dst: Reg, src: Reg) {
		when(op) {
			BinOp.ADD -> rr64(0x03, dst, src)
			BinOp.SUB -> rr64(0x2B, dst, src)
			BinOp.AND -> rr64(0x23, dst, src)
			BinOp.OR  -> rr64(0x0B, dst, src)
			BinOp.XOR -> rr64(0x33, dst, src)
			BinOp.MUL -> rr64(0xAF0F, dst, src)
			BinOp.SHL -> genShlRR(4, dst, src)
			BinOp.SHR -> genShlRR(5, dst, src)
			BinOp.DIV -> genDiv(dst) { r64(0xF7, 7, src) }
			else      -> TODO()
		}
	}



	private fun genBinOpRM(op: BinOp, dst: Reg, src: Operand) {
		when(op) {
			BinOp.ADD -> rm64(0x03, dst, src)
			BinOp.SUB -> rm64(0x2B, dst, src)
			BinOp.AND -> rm64(0x23, dst, src)
			BinOp.OR  -> rm64(0x0B, dst, src)
			BinOp.XOR -> rm64(0x33, dst, src)
			BinOp.MUL -> rm64(0xAF0F, dst, src)
			BinOp.SHL -> genShlRM(4, dst, src)
			BinOp.SHR -> genShlRM(5, dst, src)
			BinOp.DIV -> genDiv(dst) { m64(0xF7, 7, src) }
			else      -> TODO()
		}
	}



	private fun genBinOpRI(op: BinOp, dst: Reg, src: Long) {
		when(op) {
			BinOp.ADD -> r64(0x81, 0, dst).dword(src.toInt())
			BinOp.OR  -> r64(0x81, 1, dst).dword(src.toInt())
			BinOp.AND -> r64(0x81, 4, dst).dword(src.toInt())
			BinOp.SUB -> r64(0x81, 5, dst).dword(src.toInt())
			BinOp.XOR -> r64(0x81, 6, dst).dword(src.toInt())
			BinOp.MUL -> rr64(0x69, dst, dst).dword(src.toInt())
			BinOp.SHL -> r64(0xC0, 4, dst).byte(src.toInt())
			BinOp.SHR -> r64(0xC0, 5, dst).byte(src.toInt())
			BinOp.DIV -> genDiv(dst) {
				val srcReg = nextReg()
				genMovRegImm(srcReg, src)
				if(src < 0) word(0x9948) // CQO
				r64(0xF7, 7, srcReg)
			}
			else -> TODO()
		}
	}



	/**
	 * [src] must be a leaf node.
	 */
	private fun genBinOp(op: BinOp, dst: Reg, src: Node) {
		when(src) {
			is NameNode -> {
				val sym = src.exprSym ?: invalid()
				if(sym !is VarNode) invalid()
				genBinOpRM(op, dst, sym.mem!!)
			}
			is IntNode -> genBinOpRI(op, dst, src.constValue)
			is BinNode -> {
				if(src.isConst) {
					genBinOpRI(op, dst, src.constValue)
				} else {
					genBinOp(src.op, dst, src.left)
					genBinOp(op, dst, src.right)
				}
			}
			is UnNode -> {
				if(!src.isConst) invalid()
				genBinOpRI(op, dst, src.constValue)
			}
			else -> error("Invalid node: $src")
		}
	}



	private fun genMov(type: Type, dst: Reg, src: Node) {
		if(src.isConst) {
			genMovRegImm(dst, src.constValue)
			return
		}

		when(src) {
			is NameNode -> genMovRegVar(dst, src.exprSym as? VarNode ?: invalid())
			is BinNode -> {
				genMov(type, dst, src.left)
				genBinOp(src.op, dst, src.right)
			}
			else -> error("Invalid node: $src")
		}
	}



	/*
	Testing
	 */



	private val regValues = LongArray(16)

	private fun evalExpr(node: Node): Long = when(node) {
		is IntNode -> node.value
		is BinNode -> node.op.calc(evalExpr(node.left), evalExpr(node.right))
		else       -> error("Invalid node: $node")
	}

	private fun evalIns(ins: Instruction) {
		val dst = (ins.op1 as RegOperand).reg

		val srcValue = when(ins.op2) {
			is RegOperand -> regValues[ins.op2.reg.index]
			is ImmOperand -> ins.op2.value
			else          -> err()
		}

		when(ins.mnemonic) {
			Mnemonic.MOV  -> regValues[dst.index] = srcValue
			Mnemonic.ADD  -> regValues[dst.index] += srcValue
			Mnemonic.SUB  -> regValues[dst.index] -= srcValue
			Mnemonic.IMUL -> regValues[dst.index] *= srcValue
			Mnemonic.IDIV -> regValues[dst.index] /= srcValue
			Mnemonic.AND  -> regValues[dst.index] = regValues[dst.index] and srcValue
			Mnemonic.XOR  -> regValues[dst.index] = regValues[dst.index] xor srcValue
			Mnemonic.OR   -> regValues[dst.index] = regValues[dst.index] or srcValue
			else          -> error("Invalid mnemonic: ${ins.mnemonic}")
		}
	}

	private val binOps = arrayOf(BinOp.ADD, BinOp.SUB, BinOp.MUL)

	fun randomExpr(minDepth: Int = 2, maxDepth: Int = 3, depth: Int = 0): Node {
		fun bin() = BinNode(
			binOps.random(),
			randomExpr(minDepth, maxDepth, depth + 1),
			randomExpr(minDepth, maxDepth, depth + 1)
		)

		fun int() = IntNode(Random.nextLong(9) + 1)

		return when {
			depth == maxDepth -> int()
			depth < minDepth -> bin()
			Random.nextBoolean() -> int()
			else  -> bin()
		}
	}

	fun testExpr(node: Node): Boolean {
		val expectedValue: Long
		val generatedValue: Long

		try {
			println(node.exprString)
			val dst = genExpr(IntTypes.I32, node)
			printFullExpr(node)
			expectedValue = evalExpr(node)
			instructions.forEach(::evalIns)
			generatedValue = regValues[dst.index]
			for(ins in instructions) println(ins.printString)
			if(generatedValue != expectedValue) {
				System.err.println("Test failed. Expected: $expectedValue. Generated: $generatedValue")
				return false
			}
			println("Test passed\n")
		} catch(e: ArithmeticException) {
			println("Divide by zero, aborting")
		}
		return true
	}



	/*
	Basic codegen
	 */



	private fun Any.byte(value: Int) = writer.i8(value)

	private fun Any.word(value: Int) = writer.i16(value)

	private fun Any.i24(value: Int) = writer.i24(value)

	private fun Any.dword(value: Int) = writer.i32(value)

	private fun Any.qword(value: Long) = writer.i64(value)

	private fun isSigned(type: Type) = type is IntType && type.signed



	private fun Any.mem(mem: Operand, reg: Int, immWidth: Width) {
		if(mem is RelocOperand) {
			byte((reg shl 3) or 0b101)
			context.relRelocs.add(Reloc(SecPos(section, writer.pos), mem.reloc, mem.disp, immWidth, Width.DWORD))
			dword(0)
			return
		} else if(mem is StackOperand) {
			if(mem.disp.isImm8)
				byte(0b01_000_101 or (reg shl 3)).byte(mem.disp)
			else
				byte(0b10_000_101 or (reg shl 3)).dword(mem.disp)
		} else if(mem is MemOperand) {

			val i = mem.index.value
			val b = mem.base.value
			val disp = mem.disp

			val mod = if(disp == 0) 0 else if(disp.isImm8) 1 else 2
			fun disp() = if(disp == 0) Unit else if(disp.isImm8) byte(disp) else dword(disp)

			if(mem.index.isValid) { // SIB: [R*S] or [R*S+DISP] or [R+R*S] or [R+R*S+DISP]
				if(mem.index.isInvalidSibIndex) invalid()
				val s = when(mem.scale) { 1 -> 0 2 -> 1 4 -> 2 8 -> 4 else -> err("Invalid scale") }
				if(mem.base.isValid) {
					if(b == 5 && disp == 0) { // [RBP/R13+R*S] -> [RBP+R*S+DISP8]
						i24(0b01_000_100 or (reg shl 3) or (s shl 14) or (i shl 11) or (0b101 shl 8))
					} else {
						word((mod shl 6) or (reg shl 3) or 0b100 or (s shl 14) or (i shl 11) or (b shl 8))
						disp()
					}
				} else { // Index only, requires disp32
					word((reg shl 3) or 0b100 or (s shl 14) or (i shl 11) or (0b101 shl 8))
					dword(mem.disp)
				}
			} else if(mem.base.isValid) { // Indirect: [R] or [R+DISP]
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
		} else {
			invalid()
		}
	}



	private fun m64(opcode: Int, reg: Int, rm: Operand, immWidth: Width = Width.NONE) {
		byte(0x48)
		writer.varLengthInt(opcode)
		mem(rm, reg, immWidth)
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
		val rex = 0b0100_0000 or reg.rexR or rm.rexB
		if(rex != 0b0100_0000) byte(rex)
		writer.varLengthInt(opcode)
		byte(0b11_000_000 or reg.regValue or rm.rmValue)
	}

	private fun rm8(opcode: Int, reg: Reg, mem: Operand) {
		val rex = 0b0100_0000 or reg.rexR
		if(rex != 0b0100_0000 || reg.requiresRex) byte(rex)
		writer.varLengthInt(opcode)
		mem(mem, reg.value, Width.NONE)
	}

	private fun rm16(opcode: Int, reg: Reg, mem: Operand) {
		byte(0x66)
		val rex = 0b0100_0000 or reg.rexR
		if(rex != 0b0100_0000) byte(rex)
		writer.varLengthInt(opcode)
		mem(mem, reg.value, Width.NONE)
	}

	private fun rm32(opcode: Int, reg: Reg, mem: Operand) {
		val rex = 0b0100_0000 or reg.rexR
		if(rex != 0b0100_0000) byte(rex)
		writer.varLengthInt(opcode)
		mem(mem, reg.value, Width.NONE)
	}

	private fun rm64(opcode: Int, reg: Reg, mem: Operand) {
		byte(0b0100_1000 or reg.rexR)
		writer.varLengthInt(opcode)
		mem(mem, reg.value, Width.NONE)
	}




	/*
	MOV to REG
	 */



	private fun genMovRegReg(dst: Reg, src: Reg) {
		when {
			dst == src -> return
			src.isR32  -> rr32(0x89, src, dst) // 89 MOV RM32_R32
			src.isR64  -> rr64(0x89, src, dst) // RW 89 MOV RM64_R64
			else       -> err("Invalid register: $src")
		}
	}



	private fun genLea(dst: Reg, src: MemOperand) {
		word(0x8D48 or (dst.rex shl 2))
		mem(src, dst.value, Width.NONE)
	}



	private fun genMovRegImm(dst: Reg, src: Long) {
		// src == 0    -> xor dst, dst
		// src.isImm32 -> mov dst, src (RW C7/0 MOV RM64_I32) (sign-extended)
		// else        -> mov dst, src (RW B8 MOV R64_I64)
		when {
			src == 0L   -> rr32(0x31, dst, dst)
			src.isImm32 -> rr64(0xC7, Reg(0), dst).dword(src.toInt())
			else        -> word(0xB848 or dst.rex or (dst.value shl 8)).qword(src)
		}
	}



	private fun genMovRegVar(dst: Reg, src: VarNode) {
		// 1: movsx/movzx rcx, byte [src]
		// 2: movsx/movzx rcx, word [src]
		// 4s: movsxd rcx, [src]
		// 4u: mov ecx, [src]
		// 8: mov rcx, [src]
		when(src.size) {
			1 -> rm64(if(isSigned(src.type)) 0xBE0F else 0xB60F, dst, src.mem!!)
			2 -> rm64(if(isSigned(src.type)) 0xBF0F else 0xB70F, dst, src.mem!!)
			4 -> if(isSigned(src.type)) rm64(0x63, dst, src.mem!!) else rm32(0x8B, dst, src.mem!!)
			8 -> rm64(0x8B, dst, src.mem!!)
			else -> err("Invalid type size")
		}
	}


}