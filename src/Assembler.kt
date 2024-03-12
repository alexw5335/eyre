package eyre

class Assembler(private val context: Context) {


	private lateinit var file: SrcFile

	private var writer = context.textWriter

	private var section = context.textSec

	private var nodeIndex = 0

	private var currentProc: ProcNode? = null

	private var currentSrcPos: SrcPos? = null



	private fun err(message: String): Nothing =
		throw EyreError(currentSrcPos, message)

	private fun err(srcPos: SrcPos?, message: String): Nothing =
		throw EyreError(srcPos, message)

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
		for(file in context.files)
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
				is DllImportNode -> Unit
				is NamespaceNode -> assembleScope()
				is ProcNode      -> handleProc(node)
				is InsNode       -> handleIns(node)
				is CallNode      -> handleCallNode(node)
				is DoWhileNode   -> handleDoWhileNode(node)
				is WhileNode     -> handleWhileNode(node)
				is IfNode        -> handleIfNode(node)
				is VarNode       -> handleVarNode(node)
				is BinNode       -> handleBinNode(node)
				is ForNode       -> handleForNode(node)
				else             -> err(node.srcPos, "Invalid node: $node")
			}
		}
	}



	// TODO: Account for local variables in frame size
	private fun handleProc(procNode: ProcNode) {
		currentProc = procNode
		procNode.paramsStackSize = if(procNode.mostParams < 4) 0 else procNode.mostParams * 8
		procNode.stackSize = procNode.localsStackSize + procNode.paramsStackSize + procNode.regsStackSize + 32
		val padding = 16 - procNode.stackSize % 16
		procNode.stackSize = procNode.stackSize.align16()
		procNode.paramsStackPos = padding + procNode.regsStackSize + procNode.localsStackSize
		procNode.sec = section
		procNode.disp = writer.pos
		dword(0xE58948_55) // push rbp; move rbp, rsp
		if(procNode.stackSize.isImm8)
			i24(0xEC8348).byte(procNode.stackSize) // sub rsp, byte disp
		else
			i24(0xEC8148).dword(procNode.stackSize) // sub rsp, dword disp
		assembleScope()
		word(0xC3_C9) // leave; ret
		procNode.size = writer.pos - procNode.disp
	}



	private fun handleIns(insNode: InsNode) {
		assembleIns(insNode)
	}



	/*
	Resolution
	 */



	private var memOperand: MemOperand? = null

	private var currentReloc: Pos? = null



	private fun resolveMem(node: MemNode): MemOperand {
		currentReloc = null
		memOperand = node.operand
		val disp = resolveRec(node, true)
		if(!disp.isImm32) invalid()
		node.operand.disp = disp.toInt()
		node.operand.reloc = currentReloc
		return node.operand
	}

	private fun resolveConstImm(node: Node): Int {
		currentReloc = null
		memOperand = null
		val value = resolveRec(node, true)
		if(!value.isImm32) invalid()
		return value.toInt()
	}

	private fun resolveImm(node: Node): ImmOperand {
		currentReloc = null
		memOperand = null
		val value = resolveRec(if(node is ImmNode) node.child else node, true)
		return ImmOperand(currentReloc, value)
	}

	private fun resolveRec(node: Node, regValid: Boolean): Long {
		fun reloc(pos: Pos): Long {
			if(currentReloc != null || !regValid)
				err("Only one relocation allowed")
			currentReloc = pos
			return 0
		}

		fun sym(sym: Sym?): Long = when(sym) {
			null            -> err("Invalid symbol: $sym")
			is IntSym       -> sym.intValue
			is VarNode      -> TODO()
			is ProcNode     -> reloc(sym)
			is LabelNode    -> reloc(sym)
			is StringLitSym -> reloc(sym)
			else            -> err("Invalid symbol: $sym")
		}

		return when(node) {
			is IntNode -> node.value
			is UnNode -> node.calc(regValid, ::resolveRec)
			is RegNode -> {
				if(memOperand == null || memOperand!!.base.isValid || !regValid) invalid()
				memOperand!!.base = node.reg
				0
			}
			is BinNode -> if(node.op == BinOp.MUL && node.left is RegNode && node.right is IntNode) {
				if(memOperand == null || memOperand!!.index.isValid || !regValid) invalid()
				memOperand!!.index = node.left.reg
				memOperand!!.scale = node.right.value.toInt()
				0
			} else
				node.calc(regValid, ::resolveRec)
			is DotNode    -> sym(node.sym)
			is NameNode   -> sym(node.sym)
			is StringNode -> sym(node.litSym)
			is ArrayNode  -> sym(node.sym)
			is RefNode    -> node.intSupplier?.invoke() ?: err("Invalid reference")
			else          ->  err("Invalid node: $node")
		}
	}



	/*
	Encoding
	 */



	private fun Any.byte(value: Int) = writer.i8(value)
	private fun Any.word(value: Int) = writer.i16(value)
	private fun Any.i24(value: Int) = writer.i24(value)
	private fun Any.dword(value: Int) = writer.i32(value)
	private fun Any.dword(value: Long) = writer.i32(value)



	/**
	 *     w is 1 if width is QWORD (3) and widths has DWORD (2) set
	 *     w: 64-bit override
	 *     r: REG
	 *     x: INDEX
	 *     b: RM, BASE, or OPREG
	 * */
	private fun writeRex(mask: Int, width: Int, r: Reg, x: Reg, b: Reg) {
		val w = ((1 shl width) shr 3) and (mask shr 2)
		val value = (w shl 3) or (r.rex shl 2) or (x.rex shl 1) or b.rex
		if(value != 0 || r.requiresRex || b.requiresRex)
			byte(0x40 or value)
	}

	/** Add one if width is not BYTE and if widths has BYTE set */
	private fun writeOpcode(opcode: Int, mask: Int, width: Int) {
		val addition = (mask and 1) and (1 shl width).inv()
		if(opcode and 0xFF00 != 0) word(opcode + (addition shl 8)) else byte(opcode + addition)
	}

	private fun writeO16(mask: Int, width: Int) {
		if(mask != 0b10 && width == 1) writer.i8(0x66)
	}

	private fun Any.rel(operand: ImmOperand, width: Width) {
		if(operand.reloc != null)
			context.relRelocs.add(RelReloc(section, writer.pos, width, operand.reloc!!, operand.value.toInt()))
		if(!writer.writeWidth(width, operand.value))
			err("Value out of range")
	}

	private fun Any.imm64(operand: ImmOperand, width: Width) {
		if(operand.reloc != null)
			context.absRelocs.add(AbsReloc(section, writer.pos, operand.reloc!!, operand.value.toInt()))
		if(!writer.writeWidth(width, operand.value))
			err("Value out of range")
	}

	private fun Any.imm(operand: ImmOperand, width: Width) =
		imm64(operand, if(width == Width.QWORD) Width.DWORD else width)

	private fun Any.imm(immNode: ImmNode, width: Width) =
		imm64(resolveImm(immNode), if(width == Width.QWORD) Width.DWORD else width)
	
	private fun Any.rel(immNode: ImmNode, width: Width) = rel(resolveImm(immNode), width)



	private fun writeMemRip(sym: Pos, reg: Int, immWidth: Width) {
		byte((reg shl 3) or 0b101)
		context.ripRelocs.add(RipReloc(section, writer.pos, sym, 0, immWidth))
		dword(0)
	}



	private fun writeMem(mem: MemOperand, reg: Int, immWidth: Width) {
		if(mem.reloc != null) {
			byte((reg shl 3) or 0b101)
			context.ripRelocs.add(RipReloc(section, writer.pos, mem.reloc!!, mem.disp, immWidth))
			dword(0)
			return
		}

		val i = mem.index.value
		val b = mem.base.value
		val disp = mem.disp

		val mod = if(disp == 0) 0 else if(disp.isImm8) 1 else 2
		fun disp() = if(disp == 0) Unit else if(disp.isImm8) byte(disp) else dword(disp)

		if(mem.index.isValid) { // SIB: [R*S] or [R*S+DISP] or [R+R*S] or [R+R*S+DISP]
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



	/*
	Compound encoding
	 */



	private fun encode1R(opcode: Int, mask: Int, ext: Int, op1: Reg) {
		val width = op1.type - 1
		if((1 shl width) and mask == 0) invalid()
		if(mask != 0b10 && width == 1) writer.i8(0x66)
		writeRex(mask, width, Reg.NONE, Reg.NONE, op1)
		writeOpcode(opcode, mask, width)
		byte(0b11_000_000 or (ext shl 3) or op1.value)
	}

	private fun encode1O(opcode: Int, mask: Int, op1: Reg) {
		val width = op1.type - 1
		if((1 shl width) and mask == 0) invalid()
		if(mask != 0b10 && width == 1) writer.i8(0x66)
		writeRex(mask, width, Reg.NONE, Reg.NONE, op1)
		byte(opcode + (((mask and 1) and (1 shl width).inv()) shl 3) + op1.value)
	}

	private fun encode1M(opcode: Int, mask: Int, ext: Int, mem: MemOperand, immWidth: Width) {
		val width = mem.width.ordinal - 1
		if(mask.countOneBits() != 1 && (1 shl width) and mask == 0) invalid()
		if(mask != 0b10 && width == 1) writer.i8(0x66)
		writeRex(mask, width, Reg.NONE, mem.index, mem.base)
		writeOpcode(opcode, mask, width)
		writeMem(mem, ext, immWidth)
	}

	private fun encode2RMMismatch(opcode: Int, mask: Int, reg: Reg, mem: MemOperand, immWidth: Width) {
		val width = reg.type - 1
		if((1 shl width) and mask == 0) invalid()
		if(mask != 0b10 && width == 1) writer.i8(0x66)
		writeRex(mask, width, reg, mem.index, mem.base)
		writeOpcode(opcode, mask, width)
		writeMem(mem, reg.value, immWidth)
	}

	private fun encode2RRMismatch(opcode: Int, mask: Int, op1: Reg, op2: Reg) {
		val width = op1.type - 1
		if((1 shl width) and mask == 0) invalid()
		if(mask != 0b10 && width == 1) writer.i8(0x66)
		writeRex(mask, width, op1, Reg.NONE, op2)
		writeOpcode(opcode, mask, width)
		byte(0b11_000_000 or (op1.value shl 3) or (op2.value))
	}

	private fun encode2RM(opcode: Int, mask: Int, op1: Reg, op2: MemOperand, immWidth: Width) =
		if(op2.width != Width.NONE && op2.width.ordinal != op1.type)
			invalid()
		else
			encode2RMMismatch(opcode, mask, op1, op2, immWidth)

	private fun encode2RR(opcode: Int, mask: Int, op1: Reg, op2: Reg) =
		if(op1.type != op2.type)
			invalid()
		else
			encode2RRMismatch(opcode, mask, op1, op2)


	private fun encode1RM(opcode: Int, mask: Int, ext: Int, op1: OpNode, immWidth: Width) =
		if(op1 is MemNode)
			encode1M(opcode, mask, ext, op1.operand, immWidth)
		else
			encode1R(opcode, mask, ext, op1.reg)

	private fun encode2RRM(opcode: Int, mask: Int, op1: Reg, op2: OpNode, immWidth: Width) =
		if(op2 is MemNode)
			encode2RM(opcode, mask, op1, op2.operand, immWidth)
		else
			encode2RR(opcode, mask, op1, op2.reg)

	private fun encode2RMR(opcode: Int, mask: Int, op1: OpNode, op2: Reg, immWidth: Width) =
		if(op1 is MemNode)
			encode2RM(opcode, mask, op2, op1.operand, immWidth)
		else
			encode2RR(opcode, mask, op2, op1.reg)



	/*
	Assembly
	 */



	private fun assembleIns(ins: InsNode) {
		ins.mem?.let(::resolveMem)
		when {
			ins.op1 == null -> assemble0(ins.mnemonic)
			ins.op2 == null -> assemble1(ins.mnemonic, ins.op1)
			ins.op3 == null -> assemble2(ins.mnemonic, ins.op1, ins.op2)
			else            -> assemble3(ins.mnemonic, ins.op1, ins.op2, ins.op3)
		}
	}

	private fun assemble3(mnemonic: Mnemonic, op1: OpNode, op2: OpNode, op3: OpNode) { when(mnemonic) {
		Mnemonic.IMUL -> {
			if(op3 !is ImmNode) invalid()
			encode2RRM(0x69, 0b1110, op1.reg, op2, op1.width).imm(resolveImm(op3), op1.width)
		}
		else -> invalid()
	}}

	private fun assemble0(mnemonic: Mnemonic) { when(mnemonic) {
		Mnemonic.RET -> byte(0xC3)
		Mnemonic.LEAVE -> byte(0xC9)
		Mnemonic.NOP -> byte(0x90)
		Mnemonic.WAIT -> byte(0x9B)
		Mnemonic.INT1 -> byte(0xF1)
		Mnemonic.SAHF -> byte(0x9E)
		Mnemonic.LAHF -> byte(0x9F)
		Mnemonic.CBW -> word(0x9866)
		Mnemonic.CWDE -> byte(0x98)
		Mnemonic.CDQE -> word(0x9848)
		Mnemonic.CWD -> word(0x9966)
		Mnemonic.CDQ -> byte(0x99)
		Mnemonic.CQO -> word(0x9948)
		Mnemonic.INSB -> byte(0x6C)
		Mnemonic.INSW -> word(0x6D66)
		Mnemonic.INSD -> byte(0x6D)
		Mnemonic.OUTSB -> byte(0x6E)
		Mnemonic.OUTSW -> word(0x6F66)
		Mnemonic.OUTSD -> byte(0x6F)
		Mnemonic.MOVSB -> byte(0xA4)
		Mnemonic.MOVSW -> word(0xA566)
		Mnemonic.MOVSD -> byte(0xA5)
		Mnemonic.MOVSQ -> word(0xA548)
		Mnemonic.CMPSB -> byte(0xA6)
		Mnemonic.CMPSW -> word(0xA766)
		Mnemonic.CMPSD -> byte(0xA7)
		Mnemonic.CMPSQ -> word(0xA748)
		Mnemonic.STOSB -> byte(0xAA)
		Mnemonic.STOSW -> word(0xAB66)
		Mnemonic.STOSD -> byte(0xAB)
		Mnemonic.STOSQ -> word(0xAB48)
		Mnemonic.LODSB -> byte(0xAC)
		Mnemonic.LODSW -> word(0xAD66)
		Mnemonic.LODSD -> byte(0xAD)
		Mnemonic.LODSQ -> word(0xAD48)
		Mnemonic.SCASB -> byte(0xAE)
		Mnemonic.SCASW -> word(0xAF66)
		Mnemonic.SCASD -> byte(0xAF)
		Mnemonic.SCASQ -> word(0xAF48)
		Mnemonic.INT3 -> byte(0xCC)
		Mnemonic.HLT -> byte(0xF4)
		Mnemonic.CMC -> byte(0xF5)
		Mnemonic.CLC -> byte(0xF8)
		Mnemonic.STC -> byte(0xF9)
		Mnemonic.CLI -> byte(0xFA)
		Mnemonic.STI -> byte(0xFB)
		Mnemonic.CLD -> byte(0xFC)
		Mnemonic.STD -> byte(0xFD)
		Mnemonic.SYSCALL -> word(0x050F)
		Mnemonic.CPUID -> word(0xA20F)
		else -> invalid()
	}}
	
	private fun encodeJcc(opcodeOffset: Int, op1: OpNode) {
		if(op1 !is ImmNode) invalid()
		word(0x800F or (opcodeOffset shl 8))
		rel(op1, Width.DWORD)
	}

	private fun assemble1(mnemonic: Mnemonic, op1: OpNode) { when(mnemonic) {
		Mnemonic.JO -> encodeJcc(0, op1)
		Mnemonic.JNO -> encodeJcc(1, op1)
		Mnemonic.JB, Mnemonic.JNAE, Mnemonic.JC -> encodeJcc(2, op1)
		Mnemonic.JNB, Mnemonic.JAE, Mnemonic.JNC -> encodeJcc(3, op1)
		Mnemonic.JZ, Mnemonic.JE -> encodeJcc(4, op1)
		Mnemonic.JNZ, Mnemonic.JNE -> encodeJcc(5, op1)
		Mnemonic.JBE, Mnemonic.JNA -> encodeJcc(6, op1)
		Mnemonic.JNBE, Mnemonic.JA -> encodeJcc(7, op1)
		Mnemonic.JS -> encodeJcc(8, op1)
		Mnemonic.JNS -> encodeJcc(9, op1)
		Mnemonic.JP, Mnemonic.JPE -> encodeJcc(10, op1)
		Mnemonic.JNP, Mnemonic.JPO -> encodeJcc(11, op1)
		Mnemonic.JL, Mnemonic.JNGE -> encodeJcc(12, op1)
		Mnemonic.JNL, Mnemonic.JGE -> encodeJcc(13, op1)
		Mnemonic.JLE, Mnemonic.JNG -> encodeJcc(14, op1)
		Mnemonic.JNLE, Mnemonic.JG -> encodeJcc(15, op1)

		Mnemonic.INC -> encode1RM(0xFE, 0b1111, 0, op1, Width.NONE)
		Mnemonic.DEC -> encode1RM(0xFE, 0b1111, 1, op1, Width.NONE)

		Mnemonic.INT -> when {
			op1 is ImmNode -> byte(0xCD).imm(op1, Width.BYTE)
			else -> invalid()
		}

		Mnemonic.CALL -> when(op1) {
			is ImmNode -> if(op1.child is SymNode && op1.child.sym is DllImportNode) {
				byte(0xFF)
				writeMemRip(op1.child.sym as Pos, 2, Width.NONE)
			} else
				byte(0xE8).rel(op1, Width.DWORD)
			else -> encode1RM(0xFF, 0b1000, 2, op1, Width.NONE)
		}

		Mnemonic.JMP -> when(op1) {
			is ImmNode -> byte(0xE9).rel(op1, Width.DWORD)
			else -> encode1RM(0xFF, 0b1000, 4, op1, Width.NONE)
		}

		Mnemonic.PUSH -> when(op1) {
			is ImmNode -> byte(0x68).imm(op1, Width.DWORD)
			is MemNode -> encode1M(0xFF, 0b1010, 6, op1.operand, Width.NONE)
			else -> encode1O(0x50, 0b1010, op1.reg)
		}

		Mnemonic.POP -> when(op1) {
			is MemNode -> encode1M(0x8F, 0b1010, 0, op1.operand, Width.NONE)
			else -> encode1O(0x58, 0b1010, op1.reg)
		}

		Mnemonic.NOT  -> encode1RM(0xF6, 0b1111, 2, op1, Width.NONE)
		Mnemonic.NEG  -> encode1RM(0xF6, 0b1111, 3, op1, Width.NONE)
		Mnemonic.MUL  -> encode1RM(0xF6, 0b1111, 4, op1, Width.NONE)
		Mnemonic.IMUL -> encode1RM(0xF6, 0b1111, 5, op1, Width.NONE)
		Mnemonic.DIV  -> encode1RM(0xF6, 0b1111, 6, op1, Width.NONE)
		Mnemonic.IDIV -> encode1RM(0xF6, 0b1111, 7, op1, Width.NONE)

		else -> invalid()
	}}

	private fun assemble2(mnemonic: Mnemonic, op1: OpNode, op2: OpNode) { when(mnemonic) {
		Mnemonic.ADD -> encodeADD(0x00, 0, op1, op2)
		Mnemonic.OR  -> encodeADD(0x08, 1, op1, op2)
		Mnemonic.ADC -> encodeADD(0x10, 2, op1, op2)
		Mnemonic.SBB -> encodeADD(0x18, 3, op1, op2)
		Mnemonic.AND -> encodeADD(0x20, 4, op1, op2)
		Mnemonic.SUB -> encodeADD(0x28, 5, op1, op2)
		Mnemonic.XOR -> encodeADD(0x30, 6, op1, op2)
		Mnemonic.CMP -> encodeADD(0x38, 7, op1, op2)
		Mnemonic.ROL -> encodeROL(0, op1, op2)
		Mnemonic.ROR -> encodeROL(1, op1, op2)
		Mnemonic.RCL -> encodeROL(2, op1, op2)
		Mnemonic.RCR -> encodeROL(3, op1, op2)
		Mnemonic.SAL -> encodeROL(4, op1, op2)
		Mnemonic.SHL -> encodeROL(4, op1, op2)
		Mnemonic.SHR -> encodeROL(5, op1, op2)
		Mnemonic.SAR -> encodeROL(7, op1, op2)
		Mnemonic.BT  -> encodeBT(0xA30F, 4, op1, op2)
		Mnemonic.BTS -> encodeBT(0xAB0F, 5, op1, op2)
		Mnemonic.BTR -> encodeBT(0xB30F, 6, op1, op2)
		Mnemonic.BTC -> encodeBT(0xBB0F, 7, op1, op2)
		Mnemonic.BSF -> encode2RRM(0xBC0F, 0b1110, op1.reg, op2, Width.NONE)
		Mnemonic.BSR -> encode2RRM(0xBD0F, 0b1110, op1.reg, op2, Width.NONE)
		Mnemonic.TZCNT -> { byte(0xF3); encode2RRM(0xBC0F, 0b1110, op1.reg, op2, Width.NONE) }
		Mnemonic.LZCNT -> { byte(0xF3); encode2RRM(0xBD0F, 0b1110, op1.reg, op2, Width.NONE) }

		Mnemonic.IMUL -> when(op2) {
			is ImmNode -> encode2RR(0x69, 0b1110, op1.reg, op1.reg).imm(op2, op1.width)
			else -> encode2RRM(0xAF0F, 0b1110, op1.reg, op2, Width.NONE)
		}

		Mnemonic.LEA -> when(op2) {
			is MemNode -> encode2RM(0x8D, 0b1110, op1.reg, op2.operand, Width.NONE)
			else -> invalid()
		}

		Mnemonic.MOV -> when(op2) {
			is ImmNode -> when(op1) {
				is MemNode -> encode1RM(0xC6, 0b1111, 0, op1, op1.width).imm(op2, op1.width)
				else -> encode1O(0xB0, 0b1111, op1.reg).imm64(resolveImm(op2), op1.width)
			}
			is MemNode -> encode2RM(0x8A, 0b1111, op1.reg, op2.operand, Width.NONE)
			else -> encode2RMR(0x88, 0b1111, op1, op2.reg, Width.NONE)
		}

		Mnemonic.MOVSXD -> when {
			op2.width != Width.DWORD -> invalid()
			op2 is MemNode -> encode2RMMismatch(0x63, 0b1100, op1.reg, op2.operand, Width.NONE)
			else -> encode2RRMismatch(0x63, 0b1100, op1.reg, op2.reg)
		}

		Mnemonic.MOVSX -> when(op2.width) {
			Width.BYTE -> if(op2 is MemNode)
				encode2RMMismatch(0xBE0F, 0b1110, op1.reg, op2.operand, Width.NONE)
			else
				encode2RRMismatch(0xBE0F, 0b1110, op1.reg, op2.reg)
			Width.WORD -> if(op2 is MemNode)
				encode2RMMismatch(0xBF0F, 0b1100, op1.reg, op2.operand, Width.NONE)
			else
				encode2RRMismatch(0xBF0F, 0b1100, op1.reg, op2.reg)
			else -> invalid()
		}

		Mnemonic.MOVZX -> when(op2.width) {
			Width.BYTE -> if(op2 is MemNode)
				encode2RMMismatch(0xB60F, 0b1110, op1.reg, op2.operand, Width.NONE)
			else
				encode2RRMismatch(0xB60F, 0b1110, op1.reg, op2.reg)
			Width.WORD -> if(op2 is MemNode)
				encode2RMMismatch(0xB70F, 0b1100, op1.reg, op2.operand, Width.NONE)
			else
				encode2RRMismatch(0xB70F, 0b1100, op1.reg, op2.reg)
			else -> invalid()
		}

		Mnemonic.TEST -> when(op2) {
			is ImmNode -> encode1RM(0xF6, 0b1111, 0, op1, op1.width).imm(op2, op1.width)
			else -> encode2RMR(0x84, 0b1111, op1, op2.reg, Width.NONE)
		}

		Mnemonic.XCHG -> when(op2) {
			is MemNode -> encode2RRM(0x86, 0b1111, op1.reg, op2, Width.NONE)
			else -> encode2RMR(0x86, 0b1111, op1, op2.reg, Width.NONE)
		}

		Mnemonic.POPCNT -> {
			byte(0xF3)
			encode2RRM(0xB80F, 0b1110, op1.reg, op2, Width.NONE)
		}

		Mnemonic.MOVBE -> when {
			op2 is MemNode -> encode2RM(0xF0380F, 0b1110, op1.reg, op2.operand, Width.NONE)
			op1 is MemNode -> encode2RM(0xF1380F, 0b1110, op2.reg, op1.operand, Width.NONE)
			else -> invalid()
		}

		else -> invalid()
	}}

	private fun encodeADD(opcode: Int, ext: Int, op1: OpNode, op2: OpNode) {
		when(op2) {
			is ImmNode -> encode1RM(0x80, 0b1111, ext, op1, op1.width).imm(op2, op1.width)
			is MemNode -> encode2RM(opcode + 2, 0b1111, op1.reg, op2.operand, Width.NONE)
			else       -> encode2RMR(opcode + 0, 0b1111, op1, op2.reg, Width.NONE)
		}
	}

	private fun encodeBT(opcode: Int, ext: Int, op1: OpNode, op2: OpNode) {
		when(op2) {
			is ImmNode -> encode1RM(0xBA0F, 0b1110, ext, op1, Width.BYTE).imm(op2, Width.BYTE)
			else -> encode2RMR(opcode, 0b1110, op1, op2.reg, Width.NONE)
		}
	}

	private fun encodeROL(ext: Int, op1: OpNode, op2: OpNode) {
		when {
			op2.reg == Reg.CL -> encode1RM(0xD2, 0b1111, ext, op1, Width.NONE)
			op2 is ImmNode -> encode1RM(0xC0, 0b1111, 0, op1, Width.BYTE).imm(op2, Width.BYTE)
			else -> invalid()
		}
	}



	/*
	Codegen
	 */



	private fun handleBinNode(binNode: BinNode) {
		if(binNode.op != BinOp.SET)
			err("Invalid bin node")

		if(binNode.right is CallNode) {
			handleCallNode(binNode.right)
			if(binNode.left is RegNode) {
				if(!binNode.left.reg.isR64)
					err("Only R64 allowed here")
				encode2RR(0x88, 0b1111, binNode.left.reg, Reg.RAX)
				return
			} else if(binNode.left is SymNode) {
				genMovRaxToSym(binNode.srcPos, binNode.left.sym as? VarNode ?: err("Invalid receiver"))
			} else {
				err("Invalid receiver")
			}
		} else if(isImm(binNode.right)) {
			if(binNode.left is RegNode) {
				TODO()
			} else if(binNode.left is SymNode) {
				TODO()
				//val dst = binNode.left.sym as? VarNode ?: err("Invalid")
				//encodeMoveImmToSym(dst, binNode.right)
			} else {
				err("Invalid receiver")
			}
		} else {
			err("Not yet implemented")
		}
	}



	private fun handleIfNode(ifNode: IfNode) {
		if(ifNode.condition != null) {
			val condition = ifNode.condition as? BinNode ?: invalid()
			genCmpAndJmp(condition.left, condition.right, condition.op, true)
			ifNode.startJmpPos = writer.pos
			dword(0)
		}

		assembleScope()

		if(ifNode.next != null) {
			ifNode.endJmpPos = writer.pos + 1
			byte(0xE9)
			dword(0)
		} else if(ifNode.parentIf != null) {
			var parent = ifNode.parentIf
			while(parent != null) {
				val diff = writer.pos - parent.endJmpPos - 4
				writer.i32(parent.endJmpPos, diff)
				parent = parent.parentIf
			}
		}

		if(ifNode.condition != null) {
			val diff = writer.pos - ifNode.startJmpPos - 4
			writer.i32(ifNode.startJmpPos, diff)
		}
	}



	private fun handleForNode(node: ForNode) {
		if(node.range !is BinNode)
			invalid()

		handleVarNode(node.index)
		genMovNodeToSym(node.index, node.range.left)

		byte(0xE9)
		val startJmpPos = writer.pos
		dword(0)
		val start = writer.pos
		assembleScope()

		if(node.index.loc is RegVarLoc) {
			encode1R(0x80, 0b1111, 0, (node.index.loc as RegVarLoc).reg)
			imm(ImmOperand(null, 1), Width.DWORD)
		} else {
			val width = Width.fromBytes(node.index.size)
			val mem = toMem(node.index.loc).also { it.width = width }
			encode1M(0x80, 0b1111, 0, mem, width)
			imm(ImmOperand(null, 1), width)
		}

		writer.i32(startJmpPos, writer.pos - startJmpPos - 4)
		val op = when(node.range.op) {
			BinOp.TO -> BinOp.LTE
			BinOp.UNTIL -> BinOp.LT
			else -> invalid()
		}
		genCmpAndJmp(NameNode(Base.NULL, Name.NONE, node.index), node.range.right, op, false)
		dword(start - writer.pos - 4)
	}



	private fun handleWhileNode(node: WhileNode) {
		byte(0xE9)
		val startJmpPos = writer.pos
		dword(0)
		val start = writer.pos
		assembleScope()
		val condition = node.condition as? BinNode ?: invalid()
		writer.i32(startJmpPos, writer.pos - startJmpPos - 4)
		genCmpAndJmp(condition.left, condition.right, condition.op, false)
		dword(start - writer.pos - 4)
	}



	private fun handleDoWhileNode(node: DoWhileNode) {
		val start = writer.pos
		assembleScope()
		val condition = node.condition as? BinNode ?: invalid()
		genCmpAndJmp(condition.left, condition.right, condition.op, false)
		dword(start - writer.pos - 4)
	}



	private fun handleCallNode(callNode: CallNode) {
		for((i, n) in callNode.elements.withIndex()) {
			val dst = if(i >= 4) Reg.RAX else Reg.arg64(i)
			genMovNodeToReg(dst, n)
			if(i >= 4)
				genMovRaxToStackRsp(32 + (i - 4) * 8)
		}

		val call = callNode.left as? SymNode ?: err(callNode.left.srcPos, "Invalid call node")
		val sym = call.sym

		if(sym is DllImportNode) {
			byte(0xFF)
			writeMemRip(sym, 2, Width.NONE)
		} else if(sym is VarNode) {
			byte(0xFF)
			if(sym.loc is RegVarLoc)
				byte(0b11_000_010 or (sym.loc as RegVarLoc).reg.rmValue)
			else
				writeMem(toMem(sym.loc), 2, Width.NONE)
		} else if(sym is ProcNode) {
			byte(0xE8)
			context.ripRelocs.add(RipReloc(section, writer.pos, sym, 0, Width.NONE))
			dword(0)
		} else {
			err(callNode.left.srcPos, "Invalid function")
		}
	}



	/*
	Variable initialisation
	 */



	private fun handleVarNode(node: VarNode) {
		if(node.proc != null) {
			if(node.valueNode != null)
				err("Initialiser not available for local variables")

			if(node.atNode == null) {
				node.proc.localsStackSize += node.size
				node.loc = MemVarLoc(MemOperand.rbp(-node.proc.localsStackSize))
			} else if(node.atNode is RegNode) {
				node.loc = RegVarLoc(node.atNode.reg)
			} else if(node.atNode is MemNode) {
				node.loc = MemVarLoc(resolveMem(node.atNode))
			} else {
				err(node.atNode.srcPos, "Invalid explicit variable location location")
			}
		} else if(node.valueNode == null) {
			context.bssSize = context.bssSize.align(node.type.alignment)
			node.loc = GlobalVarLoc(context.bssSec, context.bssSize)
			context.bssSize += node.size
		} else {
			sectioned(context.dataSec) {
				writer.align(8)
				node.loc = GlobalVarLoc(section, writer.pos)
				writeInitialiser(node.type, 0, node.valueNode)
				writer.pos += node.type.size
			}
		}
	}



	private fun writeInitialiser(type: Type, offset: Int, node: Node) {
		if(node is InitNode) {
			if(type is StructNode) {
				if(node.elements.size > type.members.size)
					err(node.srcPos, "Too many initialiser elements")
				for(i in node.elements.indices) {
					val member = type.members[i]
					writeInitialiser(member.type, offset + member.offset, node.elements[i])
				}
			} else if(type is ArrayType) {
				if(node.elements.size > type.count)
					err(node.srcPos, "Too many initialiser elements. Found: ${node.elements.size}, expected: < ${type.count}")
				for(i in node.elements.indices)
					writeInitialiser(type.baseType, offset + type.baseType.size * i, node.elements[i])
			} else if(type is PointerType) {
				for(i in node.elements.indices)
					writeInitialiser(type.baseType, offset + type.baseType.size * i, node.elements[i])
			} else {
				err(node.srcPos, "Invalid initialiser type: ${type.name}")
			}
		} else {
			if(type is IntType) {
				val width = when(type.size) {
					1 -> Width.BYTE
					2 -> Width.WORD
					4 -> Width.DWORD
					8 -> Width.QWORD
					else -> err(node.srcPos, "Invalid initialiser size: ${type.size}")
				}
				writer.at(writer.pos + offset) { imm64(resolveImm(node), width) }
			} else if(type is StringType) {
				if(node !is StringNode) err(node.srcPos, "Invalid initialiser")
				writer.ascii(node.value)
				writer.i8(0)
			} else {
				err(node.srcPos, "Invalid initialiser")
			}
		}
	}



	/*
	Comparison codegen
	 */



	private fun conditionSI(op: BinOp): Int = when(op) {
		BinOp.EQ  -> 0x5 // NE
		BinOp.NEQ -> 0x4 // E
		BinOp.GT  -> 0xE // LE/NG
		BinOp.GTE -> 0xC // L/NGE
		BinOp.LT  -> 0xD // GE/NL
		BinOp.LTE -> 0xF // G/NLE
		else      -> invalid()
	}

	private fun conditionS(op: BinOp) = when(op) {
		BinOp.EQ  -> 0x4 // E
		BinOp.NEQ -> 0x5 // NE
		BinOp.GT  -> 0xF // G/NLE
		BinOp.GTE -> 0xD // GE/NL
		BinOp.LT  -> 0xC // L/NGE
		BinOp.LTE -> 0xE // LE/NG
		else      -> invalid()
	}

	private fun conditionU(op: BinOp) = when(op) {
		BinOp.EQ  -> 0x4 // E
		BinOp.NEQ -> 0x5 // NE
		BinOp.GT  -> 0x7 // A/NBE
		BinOp.GTE -> 0x3 // AE/NB
		BinOp.LT  -> 0x2 // B/NAE
		BinOp.LTE -> 0x6 // BE/NA
		else      -> invalid()
	}

	private fun conditionUI(op: BinOp) = when(op) {
		BinOp.EQ  -> 0x5 // NE
		BinOp.NEQ -> 0x4 // E
		BinOp.GT  -> 0x6 // BE/NA
		BinOp.GTE -> 0x2 // B/NAE
		BinOp.LT  -> 0x3 // AE/NB
		BinOp.LTE -> 0x7 // A/NBE
		else      -> invalid()
	}



	private fun genCmpRegReg(dst: Reg, src: Reg) {
		rr64(0x3B, dst, src)
	}

	private fun genCmpRegImm(dst: Reg, src: ImmOperand, op: BinOp) {
		if(src.value == 0L && (op == BinOp.EQ || op == BinOp.NEQ))
			encode2RR(0x84, 0b1111, dst, dst)
		else
			encode1R(0x80, 0b1111, 7, dst).imm(src, Width.DWORD)
	}

	private fun genCmpRegSym(dst: Reg, src: VarNode) {
		if(src.loc is RegVarLoc)
			genCmpRegReg(dst, (src.loc as RegVarLoc).reg)
		else
			encode2RM(0x3A, 0b1111, dst, toMem(src.loc!!), Width.NONE)
	}

	private fun genCmpSymReg(dst: VarNode, src: Reg) {
		if(dst.loc is RegVarLoc) {
			genCmpRegReg((dst.loc as RegVarLoc).reg, src)
		} else {
			encode2RM(0x38, 0b1111, src, toMem(dst.loc!!), Width.NONE)
		}
	}

	private fun genCmpSymImm(dst: VarNode, src: ImmOperand, op: BinOp) {
		if(dst.loc is RegVarLoc) {
			genCmpRegImm((dst.loc as RegVarLoc).reg, src, op)
		} else {
			val width = when(dst.size) {
				1 -> Width.BYTE
				2 -> Width.WORD
				4 -> Width.DWORD
				8 -> Width.QWORD
				else -> invalid()
			}
			encode1M(0x80, 0b1111, 7, toMem(dst.loc).also { it.width = width }, width)
			imm(src, width)
		}
	}



	private fun genCmpNodeNode(left: Node, right: Node, op: BinOp) {
		if(left is RegNode) {
			if(right is RegNode) {
				genCmpRegReg(left.reg, right.reg)
			} else if(isImm(right)) {
				genCmpRegImm(left.reg, resolveImm(right), op)
			} else if(right is SymNode) {
				genCmpRegSym(left.reg, right.sym as? VarNode ?: invalid())
			} else {
				invalid()
			}
		} else if(left is SymNode) {
			val dst = left.sym as? VarNode ?: invalid()

			if(right is RegNode) {
				genCmpSymReg(dst, right.reg)
			} else if(isImm(right)) {
				genCmpSymImm(dst, resolveImm(right), op)
			} else {
				invalid()
			}
		} else {
			invalid()
		}
	}



	private fun genCmpAndJmp(dst: Node, src: Node, op: BinOp, inverse: Boolean) {
		val signed = true // TODO: Set this properly
		genCmpNodeNode(dst, src, op)
		val conditionOpcode = if(inverse) if(signed) conditionSI(op) else conditionUI(op)
			else if(signed) conditionS(op) else conditionU(op)
		word(0x800F + (conditionOpcode shl 8)) // doesn't write the REL32 nor any relocations
	}



	/*
	Codegen
	- Only R64 is allowed for DST registers when set by the user to avoid worrying about width mismatch
	- Width of DST registers is irrelevant. Final width is chosen by variable type, imm width, etc.
	- Any literal immediate values and registers are assumed to be signed
	 */



	private fun toMem(loc: VarLoc?) = when(loc) {
		is GlobalVarLoc -> MemOperand.rip(loc)
		is MemVarLoc -> loc.operand
		else -> invalid()
	}

	// Unsigned for non-integer types
	private fun isSigned(type: Type) = type is IntType && type.signed

	private fun isImm(node: Node) =
		node is IntNode ||
		node is BinNode ||
		node is UnNode ||
		(node is SymNode && node.sym is IntSym)

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
	RAX mov
	 */



	private fun genMovRaxToStackRsp(disp: Int) {
		if(disp.isImm8)
			dword(0x24448948).byte(disp)  // mov [rsp + disp8], rax
		else
			dword(0x24848948).dword(disp) // mov [rsp + disp32], rax
	}



	private fun genMovRaxToSym(srcPos: SrcPos?, src: VarNode) {
		if(src.loc is RegVarLoc) {
			val reg = (src.loc as RegVarLoc).reg
			word(0x8948)
			byte(0b11_000_000 or reg.rmValue)
			return
		}

		val mem = toMem(src.loc)

		// Sign doesn't matter here
		when(src.type.size) {
			1    -> byte(0x88)
			2    -> word(0x8866)
			4    -> byte(0x89)
			8    -> word(0x8948)
			else -> err(srcPos, "Invalid type size")
		}
		writeMem(mem, 0, Width.NONE)
	}



	/*
	MOV to SYM
	 */



	private fun genMovNodeToSym(dst: VarNode, src: Node) {
		if(src is RegNode) {
			genMovRegToSym(dst, src.reg)
		} else if(isImm(src)) {
			genMovImmToSym(dst, resolveImm(src))
		} else {
			invalid()
		}
	}



	private fun genMovRegToSym(dst: VarNode, src: Reg) {
		if(dst.loc is RegVarLoc) {
			genMovRegToReg((dst.loc as RegVarLoc).reg, src)
		} else {
			val mem = toMem(dst.loc)
			when(dst.size) {
				1 -> rm8(0x88, src, mem)
				2 -> rm16(0x89, src, mem)
				4 -> rm32(0x89, src, mem)
				8 -> rm64(0x89, src, mem)
				else -> invalid()
			}
		}
	}



	private fun genMovImmToSym(dst: VarNode, src: ImmOperand) {
		if(dst.loc is RegVarLoc) {
			genMovImmToReg((dst.loc as RegVarLoc).reg, src)
			return
		}

		val mem = toMem(dst.loc)

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
				imm64(src, Width.BYTE)
			}
			2 -> {
				word(0xC766)
				writeMem(mem, 0, Width.WORD)
				imm64(src, Width.WORD)
			}
			4 -> {
				byte(0xC7)
				writeMem(mem, 0, Width.DWORD)
				imm64(src, Width.DWORD)
			}
			8 -> {
				if(src.value.isImm32) {
					if(isSigned(dst.type)) {
						word(0xC748)
						writeMem(mem, 0, Width.DWORD)
						imm64(src, Width.DWORD)
					} else {
						byte(0xC7)
						writeMem(mem, 0, Width.DWORD)
						imm64(src, Width.DWORD)
					}
				} else {
					word(0xB048) // mov rax, qword src
					imm64(src, Width.QWORD)
					byte(0x89) // mov [dst], rax
					writeMem(mem, 0, Width.NONE)
				}
			}
		}
	}



	/*
	MOV to REG
	 */



	private fun genMovNodeToReg(dst: Reg, src: Node) {
		if(src is RegNode) {
			genMovRegToReg(dst, src.reg)
		} else if(src is StringNode) {
			genMovPtrToReg(dst, MemOperand.rip(src.litSym!!))
		} else if(src is UnNode && src.op == UnOp.ADDR) {
			val sym = (src.child as? SymNode)?.sym as? Pos ?: err("Invalid address-of")
			genMovPtrToReg(dst, MemOperand.rip(sym))
		} else if(isImm(src)) {
			genMovImmToReg(dst, resolveImm(src))
		} else if(src is SymNode) {
			genMovSymToReg(dst, src.sym as? VarNode ?: invalid())
		} else {
			invalid()
		}
	}



	private fun genMovRegToReg(dst: Reg, src: Reg) {
		when {
			dst == src -> return
			src.isR32  -> rr32(0x89, src, dst) // 89 MOV RM32_R32
			src.isR64  -> rr64(0x89, src, dst) // RW 89 MOV RM64_R64
			else       -> err("Invalid register: $src")
		}
	}


	private fun genMovPtrToReg(dst: Reg, src: MemOperand) {
		word(0x8D48 or (dst.rex shl 2))
		writeMem(src, dst.value, Width.NONE)
	}



	private fun genMovImmToReg(dst: Reg, src: ImmOperand) {
		// src == 0    -> xor dst, dst
		// src.isImm32 -> mov dst, src (RW C7/0 MOV RM64_I32)(sign-extended)
		// else        -> mov dst, src (RW B8 MOV R64_I64)
		when {
			src.value == 0L   -> rr32(0x31, dst, dst)
			src.value.isImm32 -> rr64(0xC7, Reg(0), dst).imm64(src, Width.DWORD)
			else              -> word(0xB848 or dst.rex or (dst.value shl 8)).imm64(src, Width.QWORD)
		}
	}



	private fun genMovSymToReg(dst: Reg, src: VarNode) {
		if(src.type is StringType) {
			genMovPtrToReg(dst, toMem(src.loc))
			return
		}

		if(src.loc is RegVarLoc) {
			genMovRegToReg(dst, (src.loc as RegVarLoc).reg)
			return
		}

		val mem = toMem(src.loc)

		// 1: movsx/movzx rcx, byte [src]
		// 2: movsx/movzx rcx, word [src]
		// 4s: movsxd rcx, [src]
		// 4u: mov ecx, [src]
		// 8: mov rcx, [src]
		when(src.size) {
			1 -> rm64(if(isSigned(src.type)) 0xBE0F else 0xB60F, dst, mem)
			2 -> rm64(if(isSigned(src.type)) 0xBF0F else 0xB70F, dst, mem)
			4 -> if(isSigned(src.type)) rm64(0x63, dst, mem) else rm32(0x8B, dst, mem)
			8 -> rm64(0x8B, dst, mem)
			else -> err("Invalid type size")
		}
	}


}