package eyre

@Suppress("UnusedReceiverParameter")
class Assembler(private val context: Context) {


	private lateinit var file: SrcFile

	private var writer = context.textWriter

	private var section = context.textSec

	private var nodeIndex = 0

	private var currentFun: FunNode? = null

	private var currentSrcPos: SrcPos? = null

	private val bodyInstructions = ArrayList<Instruction>()

	private val prologueInstructions = ArrayList<Instruction>()

	private val relocs = ArrayList<Reloc>()



	private fun err(message: String): Nothing =
		throw EyreError(currentSrcPos, message)

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		throw EyreError(srcPos, message)

	private fun err(node: Node, message: String): Nothing =
		throw EyreError(node.srcPos, message)

	private fun invalid(): Nothing =
		throw EyreError(currentSrcPos, "Invalid encoding")

	private inline fun sectioned(sec: Section, block: () -> Unit) {
		val prevSec = this.section
		val prevWriter = this.writer
		this.section = sec
		this.writer = sec.writer!!
		block()
		this.section = prevSec
		this.writer = prevWriter
	}



	/*
	Assembly
	 */



	fun assemble() {
		assembleStringLiterals()
		for (file in context.files)
			assemble(file)
	}



	private fun assembleStringLiterals() {
		sectioned(context.dataSec) {
			for(sym in context.stringLiterals) {
				writer.align(8)
				sym.sec = section
				sym.disp = writer.pos
				writer.asciiNT(sym.value)
			}
		}
	}



	private fun assemble(file: SrcFile) {
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
				is BinNode       -> assembleBinNode(node)
				is CallNode      -> genCall(node)
				is VarNode       -> { }
				else             -> err(node.srcPos, "Invalid node: $node")
			}
		}
	}



	/*
	Registers
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

	private fun ins(mnemonic: Mnemonic, op1: Operand? = null, op2: Operand? = null) =
		bodyInstructions.add(Instruction(mnemonic, op1, op2))



	/*
	Code generation
	 */



	private fun assembleBinNode(node: BinNode) {
		if(node.op != BinOp.SET) invalid()
		val dst = (node.left as? SymNode)?.sym as? VarNode ?: invalid()
		val reg = getReg()
		genExprRec(reg, node.right)

		val dstOperand = when(dst.mem) {
			is GlobalMem -> MemOperand.reloc(Width.NONE, dst.mem)
			is StackMem  -> MemOperand.rbp(Width.NONE, dst.mem.disp)
			is RegMem    -> RegOperand(dst.mem.reg)
		}

		ins(Mnemonic.MOV, dstOperand, RegOperand(reg))
		freeReg(reg)
	}



	private fun assembleFunction(function: FunNode) {
		currentFun = function

		function.stackPos = -32
		for(local in function.locals) {
			val mem = local.mem as? StackMem ?: invalid()
			function.stackPos -= local.size
			mem.disp = function.stackPos
		}

		assembleScope()

		//stackPos = stackPos.align16()
		//prologueInstructions.add(Instruction(Mnemonic.PUSH, RegOperand(Reg.RBP)))
		//prologueInstructions.add(Instruction(Mnemonic.MOV, RegOperand(Reg.RBP), RegOperand(Reg.RSP)))
		//prologueInstructions.add(Instruction(Mnemonic.SUB, RegOperand(Reg.RSP), ImmOperand(-stackPos)))
		//bodyInstructions.add(Instruction(Mnemonic.LEAVE))
		//bodyInstructions.add(Instruction(Mnemonic.RET))

		println("FUNCTION ${function.name}")
		prologueInstructions.forEach(::println)
		bodyInstructions.forEach(::println)
		prologueInstructions.clear()
		bodyInstructions.clear()
		println()
	}



	private val BinOp.arithmeticMnemonic get() = when(this) {
		BinOp.ADD -> Mnemonic.ADD
		BinOp.SUB -> Mnemonic.SUB
		BinOp.MUL -> Mnemonic.IMUL
		BinOp.XOR -> Mnemonic.XOR
		BinOp.OR  -> Mnemonic.OR
		BinOp.AND -> Mnemonic.AND
		else      -> invalid()
	}



	private fun VarNode.toMem() = when(mem) {
		is GlobalMem -> MemOperand.reloc(Width.NONE, mem)
		is StackMem -> MemOperand.rbp(Width.NONE, mem.disp)
		is RegMem -> invalid()
	}

	private fun Node.toOperand(): Operand = when(this) {
		is VarNode -> when(mem) {
			is GlobalMem -> MemOperand.reloc(Width.NONE, mem)
			is StackMem -> MemOperand.rbp(Width.NONE, mem.disp)
			is RegMem -> RegOperand(mem.reg)
		}
		is SymNode -> sym?.toOperand() ?: invalid()
		is IntNode -> ImmOperand(value)
		else -> invalid()
	}



	private var hasDiv = false

	private fun checkExpr(node: Node) {
		when(node) {
			is UnNode -> checkExpr(node.child)
			is BinNode -> {
				if(node.op == BinOp.DIV) hasDiv = true
				checkExpr(node.left)
				checkExpr(node.right)
			}
			is CallNode -> {
				val disp = allocateStack(8)
				val dst = MemOperand.rbp(Width.NONE, disp)
				genCall(node)
				ins(Mnemonic.MOV, dst, RegOperand(Reg.RAX))
				node.operand = dst
			}
		}
	}

	private fun checkTypes(a: Type, b: Type): Boolean = when(a) {
		is PointerType -> b is PointerType && checkTypes(a.type, b.type)
		is IntType     -> b is IntType
		else           -> a == b
	}

	private fun firstNode(node: Node): Node = when(node) {
		is BinNode -> firstNode(node.left)
		is UnNode  -> firstNode(node.child)
		else       -> node
	}

	private fun exprType(node: Node): Type = when(val first = firstNode(node)) {
		is SymNode  -> (first.sym as? TypedSym)?.type ?: invalid()
		is IntNode  -> IntTypes.I32
		is CallNode -> (first.receiver as? FunNode)?.returnType ?: invalid()
		else        -> invalid()
	}

	private fun allocateStack(size: Int): Int {
		val function = currentFun!!
		val disp = function.stackPos
		function.stackPos -= size
		return disp
	}



	/*
	Codegen
	 */



	private fun genCall(call: CallNode) {
		val function = (call.left as? SymNode)?.sym as? FunNode ?: invalid()
		if(call.args.size != function.params.size) invalid()
		for((argIndex, arg) in call.args.withIndex()) {
			val param = function.params[argIndex]
			val argType = exprType(arg)
			if(!checkTypes(param.type, argType)) invalid()
		}
	}



	private fun genExpr(target: Reg, node: Node) {
		hasDiv = false
		checkExpr(node)
		if(hasDiv) getReg(Reg.RAX)
		genExprRec(target, node)
		if(hasDiv) freeReg(Reg.RAX)
	}



	private fun genExprRec(target: Reg, node: Node) {
		val targetOp = RegOperand(target)
		if(node is BinNode) {
			if(node.left is BinNode) {
				if(node.right is BinNode) {
					genExprRec(target, node.left)
					val next = getReg()
					genExprRec(next, node.right)
					ins(node.op.arithmeticMnemonic, targetOp, RegOperand(next))
					freeReg(next)
				} else {
					genExprRec(target, node.left)
					ins(node.op.arithmeticMnemonic, targetOp, node.right.toOperand())
				}
			} else if(node.right is BinNode) {
				genExprRec(target, node.right)
				ins(node.op.arithmeticMnemonic, targetOp, node.left.toOperand())
			} else {
				ins(Mnemonic.MOV, targetOp, node.left.toOperand())
				ins(node.op.arithmeticMnemonic, targetOp, node.right.toOperand())
			}
		} else {
			genMovRegNode(target, node)
		}
	}



	/**
	 * [opcode] is the opcode for R32_RM32
	 */
	private fun genBinReg(opcode: Int, dst: Reg, src: VarNode) {
		if(src.mem is RegMem) {
			rr64(opcode, dst, src.mem.reg)
			return
		} else {
			if(src.size != 8) invalid()
			rm64(opcode, dst, src.toMem())
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



	private fun ripReloc(target: Pos, disp: Int, immWidth: Width) {
		val reloc = Reloc(section, writer.pos, target, disp, immWidth, Width.DWORD)
		relocs.add(reloc)
		context.ripRelocs.add(reloc)
	}



	private fun writeMem(mem: MemOperand, reg: Int, immWidth: Width) {
		if(mem.reloc != null) {
			byte((reg shl 3) or 0b101)
			ripReloc(mem.reloc, mem.disp, immWidth)
			dword(0)
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



	private fun genMovSymReg(dst: VarNode, src: Reg) {
		if(dst.mem is RegMem) {
			genMovRegReg(dst.mem.reg, src)
		} else {
			when(dst.size) {
				1 -> rm8(0x88, src, dst.toMem())
				2 -> rm16(0x89, src, dst.toMem())
				4 -> rm32(0x89, src, dst.toMem())
				8 -> rm64(0x89, src, dst.toMem())
				else -> invalid()
			}
		}
	}



	private fun genMovVarImm(dst: VarNode, src: Long) {
		if(dst.mem is RegMem) {
			genMovRegImm(dst.mem.reg, src)
			return
		}

		val mem = dst.toMem()

		// 1:   mov byte [rcx], 10
		// 2:   mov word [rcx], 10
		// 4:   mov dword [rcx], 10
		// 8u:  mov dword [rcx], 10
		// 8s:  mov qword [rcx], 10
		// I64: mov rax, 1 << 40; mov qword [rcx], rax
		when(dst.size) {
			1 -> {
				byte(0xC6)
				writeMem(mem, 0, Width.BYTE)
				if(src !in Width.BYTE) invalid()
				byte(src.toInt())
			}
			2 -> {
				word(0xC766)
				writeMem(mem, 0, Width.WORD)
				if(src !in Width.WORD) invalid()
				word(src.toInt())
			}
			4 -> {
				byte(0xC7)
				writeMem(mem, 0, Width.DWORD)
				if(src !in Width.DWORD) invalid()
				dword(src.toInt())
			}
			8 -> if(src.isImm32) {
				if(isSigned(dst.type)) {
					word(0xC748)
					writeMem(mem, 0, Width.DWORD)
					dword(src.toInt())
				} else {
					byte(0xC7)
					writeMem(mem, 0, Width.DWORD)
					dword(src.toInt())
				}
			} else {
				word(0xB048) // mov rax, qword src
				qword(src)
				byte(0x89) // mov [dst], rax
				writeMem(mem, 0, Width.NONE)
			}
		}
	}



	/*
	MOV to REG
	 */



	private fun genMovRegNode(dst: Reg, node: Node) {
		if(node is IntNode) {
			ins(Mnemonic.MOV, RegOperand(dst), ImmOperand(node.value))
			genMovRegImm(dst, node.value)
		} else if(node is SymNode) {
			val src = node.sym as? VarNode ?: invalid()
			ins(Mnemonic.MOV, RegOperand(dst), src.toOperand())
			genMovRegVar(dst, src)
		} else {
			invalid()
		}
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
		if(src.mem is RegMem) {
			genMovRegReg(dst, src.mem.reg)
			return
		}

		// 1: movsx/movzx rcx, byte [src]
		// 2: movsx/movzx rcx, word [src]
		// 4s: movsxd rcx, [src]
		// 4u: mov ecx, [src]
		// 8: mov rcx, [src]
		when(src.size) {
			1 -> rm64(if(isSigned(src.type)) 0xBE0F else 0xB60F, dst, src.toMem())
			2 -> rm64(if(isSigned(src.type)) 0xBF0F else 0xB70F, dst, src.toMem())
			4 -> if(isSigned(src.type)) rm64(0x63, dst, src.toMem()) else rm32(0x8B, dst, src.toMem())
			8 -> rm64(0x8B, dst, src.toMem())
			else -> err("Invalid type size")
		}
	}



}