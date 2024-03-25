package eyre

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



	fun assemble(file: SrcFile) {
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
				is BinNode       -> assembleBinNode(node)
				else             -> err("Invalid node: $node")
			}
		}
	}



	/*
	Code generation
	 */



	private var volatileRegs = 0b00001111_00000111

	private fun getReg(): Reg {
		if(volatileRegs == 0) invalid()
		val reg = volatileRegs.countTrailingZeroBits()
		volatileRegs = volatileRegs xor (1 shl reg)
		return Reg.r64(reg)
	}

	private fun getReg(reg: Reg) {
		val index = reg.index
		if(volatileRegs and (1 shl index) == 0) invalid()
		volatileRegs = volatileRegs xor (1 shl index)
	}

	private fun freeReg(reg: Reg) {
		volatileRegs = volatileRegs or (1 shl reg.index)
	}

	private fun assembleFunction(function: FunNode) {
		currentFun = function

		function.stackPos = -32
		for(param in function.params) {
			function.stackPos -= param.size
			param.mem.base = Reg.RBP
			param.mem.disp = function.stackPos
		}
		for(local in function.locals) {
			function.stackPos -= local.size
			local.mem.base = Reg.RBP
			local.mem.disp = function.stackPos
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
		val dst = (node.left.exprSym as? VarNode) ?: invalid()
		val reg = getReg()
		genExprRec(reg, node.right)
		genMovVarReg(dst, reg)
	}



	private fun genExprRec(dst: Reg, node: Node) {
		if(node is BinNode) {
			if(node.left is BinNode) {
				if(node.right is BinNode) {
					genExprRec(dst, node.left)
					val next = getReg()
					genExprRec(next, node.right)
					genBinRegReg(node.op, dst, next)
					freeReg(next)
				} else {
					genExprRec(dst, node.left)
					genBinRegNode(node.op, dst, node.right)
				}
			} else if(node.right is BinNode) {
				genExprRec(dst, node.right)
				genBinRegNode(node.op, dst, node.left)
			} else {
				genMovRegNode(dst, node.left)
				genBinRegNode(node.op, dst, node.right)
			}
		} else {
			genMovRegNode(dst, node)
		}
	}

	private fun BinOp.arithmeticExt() = when(this) {
		BinOp.ADD -> 0
		BinOp.OR -> 1
		BinOp.AND -> 4
		BinOp.SUB -> 5
		BinOp.XOR -> 6
		else -> invalid()
	}

	private fun BinOp.arithmeticOpcode() = when(this) {
		BinOp.ADD -> 0x00
		BinOp.OR -> 0x08
		BinOp.AND -> 0x20
		BinOp.SUB -> 0x28
		BinOp.XOR -> 0x30
		else -> invalid()
	}



	/*
	Arithmetic operations
	 */



	private fun genBinRegNode(op: BinOp, dst: Reg, src: Node) {
		when(op) {
			BinOp.MUL -> genMulRegNode(dst, src)
			else -> genArithmeticRegNode(op, dst, src)
		}
	}

	private fun genBinRegReg(op: BinOp, dst: Reg, src: Reg) {
		when(op) {
			BinOp.MUL -> genMulRegReg(dst, src)
			else -> genArithmeticRegReg(op.arithmeticOpcode(), dst, src)
		}
	}

	/*
	F6/5   IMUL  RM       1111
	0F AF  IMUL  R_RM     1110
	6B     IMUL  R_RM_I8  1110
	69     IMUL  R_RM_I   1110
	 */
	private fun genMulRegReg(dst: Reg, src: Reg) {
		byte(0b0100_1000 or dst.rexR or src.rexB)
		word(0xAF0F)
		byte(0b11_000_000 or dst.regValue or src.rmValue)
	}
	private fun genMulRegImm(dst: Reg, src: Long) {
		if(!src.isImm32) {
			val dst2 = getReg()
			byte(0b0100_1000 or dst2.rexR)
			byte(0xB8 or dst2.value)
			qword(src)
			genMulRegReg(dst, dst2)
		} else {
			byte(0b0100_1000 or dst.rexR or dst.rexB)
			byte(0x69)
			byte(0b11_000_000 or dst.regValue or dst.rmValue)
			if(!src.isImm32) invalid()
			dword(src.toInt())
		}
	}

	private fun genMulRegVar(dst: Reg, src: VarNode) {
		if(src.size != 4) invalid()
		rm64(0xAF0F, dst, src.mem)
	}
	private fun genMulRegNode(dst: Reg, src: Node) {
		when(src) {
			is IntNode -> genMulRegImm(dst, src.value)
			else -> genMulRegVar(dst, src.exprSym as? VarNode ?: invalid())
		}
	}



	private fun genArithmeticRegImm(ext: Int, dst: Reg, src: Long) {
		// 80/0 ADD RM_I 1111
		byte(0b0100_1000 or dst.rexB)
		byte(0x81)
		byte(0b11_000_000 or (ext shl 3) or dst.rmValue)
		if(!src.isImm32) invalid()
		dword(src.toInt())
	}
	private fun genArithmeticRegReg(opcode: Int, dst: Reg, src: Reg) {
		// 00 ADD RM_R 1111
		rr64(opcode + 1, dst, src)
	}
	private fun genArithmeticRegVar(opcode: Int, dst: Reg, src: VarNode) {
		// 02 ADD R_RM 1111
		if(src.size != 4) invalid()
		rm64(opcode + 3, dst, src.mem)
	}
	private fun genArithmeticRegNode(op: BinOp, dst: Reg, src: Node) {
		val ext = op.arithmeticExt()
		val opcode = op.arithmeticOpcode()

		when(src) {
			is IntNode -> genArithmeticRegImm(ext, dst, src.value)
			else -> genArithmeticRegVar(opcode, dst, src.exprSym as? VarNode ?: invalid())
		}
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



	private fun writeRipMem(target: SecPos, reg: Int, immWidth: Width) {
		byte((reg shl 3) or 0b101)
		context.ripRelocs.add(Reloc(SecPos(section, writer.pos), target, 0, immWidth, Width.DWORD))
	}



	private fun writeRbpMem(disp: Int, reg: Int) {
		if(disp.isImm8) {
			byte(0b01_000_101 or (reg shl 3))
			byte(disp)
		} else {
			byte(0b10_000_101 or (reg shl 3))
			dword(disp)
		}
	}



	private fun writeMem(mem: MemOperand, reg: Int, immWidth: Width) {
		if(mem.reloc != null) {
			writeRipMem(mem.reloc!!, reg, immWidth)
			return
		}

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

	private fun rm8(opcode: Int, reg: Reg, mem: MemOperand) {
		val rex = 0b0100_0000 or reg.rexR
		if(rex != 0b0100_0000 || reg.requiresRex) byte(rex)
		writer.varLengthInt(opcode)
		writeMem(mem, reg.value, Width.NONE)
	}

	private fun rm16(opcode: Int, reg: Reg, mem: MemOperand) {
		byte(0x66)
		val rex = 0b0100_0000 or reg.rexR
		if(rex != 0b0100_0000) byte(rex)
		writer.varLengthInt(opcode)
		writeMem(mem, reg.value, Width.NONE)
	}

	private fun rm32(opcode: Int, reg: Reg, mem: MemOperand) {
		val rex = 0b0100_0000 or reg.rexR
		if(rex != 0b0100_0000) byte(rex)
		writer.varLengthInt(opcode)
		writeMem(mem, reg.value, Width.NONE)
	}

	private fun rm64(opcode: Int, reg: Reg, mem: MemOperand) {
		byte(0b0100_1000 or reg.rexR)
		writer.varLengthInt(opcode)
		writeMem(mem, reg.value, Width.NONE)
	}



	/*
	MOV to SYM
	 */



	private fun genMovVarReg(dst: VarNode, src: Reg) {
		when(dst.size) {
			1 -> rm8(0x88, src, dst.mem)
			2 -> rm16(0x89, src, dst.mem)
			4 -> rm32(0x89, src, dst.mem)
			8 -> rm64(0x89, src, dst.mem)
			else -> invalid()
		}
	}



	private fun genMovVarImm(dst: VarNode, src: Long) {
		// 1:   mov byte [rcx], 10
		// 2:   mov word [rcx], 10
		// 4:   mov dword [rcx], 10
		// 8u:  mov dword [rcx], 10
		// 8s:  mov qword [rcx], 10
		// I64: mov rax, 1 << 40; mov qword [rcx], rax
		when(dst.size) {
			1 -> {
				byte(0xC6)
				writeMem(dst.mem, 0, Width.BYTE)
				if(src !in Width.BYTE) invalid()
				byte(src.toInt())
			}
			2 -> {
				word(0xC766)
				writeMem(dst.mem, 0, Width.WORD)
				if(src !in Width.WORD) invalid()
				word(src.toInt())
			}
			4 -> {
				byte(0xC7)
				writeMem(dst.mem, 0, Width.DWORD)
				if(src !in Width.DWORD) invalid()
				dword(src.toInt())
			}
			8 -> if(src.isImm32) {
				if(isSigned(dst.type)) {
					word(0xC748)
					writeMem(dst.mem, 0, Width.DWORD)
					dword(src.toInt())
				} else {
					byte(0xC7)
					writeMem(dst.mem, 0, Width.DWORD)
					dword(src.toInt())
				}
			} else {
				word(0xB048) // mov rax, qword src
				qword(src)
				byte(0x89) // mov [dst], rax
				writeMem(dst.mem, 0, Width.NONE)
			}
		}
	}



	/*
	MOV to REG
	 */



	private fun genMovRegNode(dst: Reg, node: Node) {
		if(node.exprSym != null)
			genMovRegVar(dst, node.exprSym as? VarNode ?: invalid())
		else if(node is IntNode)
			genMovRegImm(dst, node.value)
		else
			invalid()
	}



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
		writeMem(src, dst.value, Width.NONE)
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
			1 -> rm64(if(isSigned(src.type)) 0xBE0F else 0xB60F, dst, src.mem)
			2 -> rm64(if(isSigned(src.type)) 0xBF0F else 0xB70F, dst, src.mem)
			4 -> if(isSigned(src.type)) rm64(0x63, dst, src.mem) else rm32(0x8B, dst, src.mem)
			8 -> rm64(0x8B, dst, src.mem)
			else -> err("Invalid type size")
		}
	}



}