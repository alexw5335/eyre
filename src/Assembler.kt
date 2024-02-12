package eyre

class Assembler(private val context: Context) {


	private var writer = context.textWriter

	private var section = context.textSec

	private lateinit var file: SrcFile

	private lateinit var currentIns: InsNode



	fun assemble() {
		writeStringLiterals()
		for(file in context.files) {
			this.file = file
			file.nodes.forEach(::handleNode)
		}
	}



	private inline fun sectioned(sec: Section, writer: BinWriter, block: () -> Unit) {
		val prevSec = this.section
		val prevWriter = this.writer
		this.section = sec
		this.writer = writer
		block()
		this.section = prevSec
		this.writer = prevWriter
	}



	private fun writeStringLiterals() {
		sectioned(context.dataSec, context.dataWriter) {
			for(node in context.stringLiterals) {
				writer.align(8)
				node.litPos = Pos(section, writer.pos)
				writer.asciiNT(node.value)
			}
		}
	}



	private fun handleNode(node: Node) {
		try {
			when(node) {
				is VarNode -> handleVarNode(node)
				is LabelNode -> {
					node.sec = section
					node.disp = writer.pos
				}
				is NamespaceNode -> node.children.forEach(::handleNode)
				is ProcNode -> {
					node.sec = section
					node.disp = writer.pos
					node.children.forEach(::handleNode)
					node.size = writer.pos - node.disp
				}
				is DllCallNode -> handleDllCall(node)
				is InsNode -> assembleIns(node)
			}
		} catch(e: EyreError) {
			file.invalid = true
		}
	}



	private fun handleVarNode(node: VarNode) {
		if(node.valueNode == null) {
			context.bssSize = context.bssSize.align(node.type.alignment)
			node.sec = context.bssSec
			node.disp = context.bssSize
			context.bssSize += node.size
		} else {
			sectioned(context.dataSec, context.dataWriter) {
				writer.align(8)
				node.sec = section
				node.disp = writer.pos
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
			} else {
				err(node.srcPos, "Invalid initialiser type: ${type.name}")
			}
		} else {
			if(type is IntType) {
				val value = resolveRec(node, false)
				val width = when(type.size) {
					1 -> Width.BYTE
					2 -> Width.WORD
					4 -> Width.DWORD
					else -> err(node.srcPos, "Invalid initialiser size: ${type.size}")
				}
				writer.at(writer.pos + offset) {
					writer.writeWidth(width, value)
				}
			} else if(type is StringType) {
				if(node !is StringNode) err(node.srcPos, "Invalid initialiser")
				writer.ascii(node.value)
				writer.i8(0)
			} else {
				err(node.srcPos, "Invalid initialiser")
			}
		}
	}



	// Errors



	private fun err(srcPos: SrcPos?, message: String): Nothing =
		context.err(srcPos, message)

	private fun invalid(message: String = "Invalid encoding"): Nothing =
		context.err(currentIns.srcPos, message)



	// Resolution



	private var base = Reg.NONE
	private var index = Reg.NONE
	private var scale = 0
	private var relocs = 0
	private var disp = 0L



	private fun addLinkReloc(width: Width, node: Node, offset: Int, rel: Boolean) =
		context.linkRelocs.add(Reloc(Pos(section, writer.pos), node, width, offset, rel))

	private fun addAbsReloc(node: Node) =
		context.absRelocs.add(Reloc(Pos(section, writer.pos), node, Width.QWORD, 0, false))

	private fun resolveMem(node: OpNode) {
		base = Reg.NONE
		index = Reg.NONE
		scale = 0
		relocs = 0
		disp = resolveRec(node.child!!, true)
	}

	private fun resolveRec(node: Node, regValid: Boolean): Long {
		fun posSym() {
			if(relocs++ == 0 && !regValid)
				err(node.srcPos, "First relocation (absolute or relative) must be positive and absolute")
		}

		fun sym(sym: Sym?): Long {
			if(sym == null) err(node.srcPos, "Unresolved symbol")
			if(sym is PosSym) { posSym(); return 0 }
			err(node.srcPos, "Invalid node")
		}

		fun sib(reg: Reg, other: Node) {
			if(reg.type != OpType.R64)
				err(node.srcPos, "Only R64 allowed for memory operands")
			if(other !is IntNode || !other.value.isImm32)
				err(other.srcPos, "Invalid scale")
			if(index != Reg.NONE)
				err(other.srcPos, "Multiple index registers")
			scale = other.value.toInt()
			index = reg
		}

		if(node is StringNode) { posSym(); return 0 }
		if(node is OpNode)   return resolveRec(node.child!!, regValid)
		if(node is IntNode)  return node.value
		if(node is UnNode)   return node.calc(regValid, ::resolveRec)
		if(node is NameNode) return sym(node.sym)
		if(node is DotNode)  return sym(node.sym)
		if(node is ArrayNode) return sym(node.sym)

		if(node is BinNode) {
			if(node.op == BinOp.MUL) {
				if(node.left is RegNode) {
					sib(node.left.value, node.right)
					return 0
				} else if(node.right is RegNode) {
					sib(node.right.value, node.left)
					return 0
				}
			}
			return node.calc(regValid, ::resolveRec)
		}

		if(node is RegNode) {
			if(node.value.type != OpType.R64)
				err(node.srcPos, "Only R64 allowed for memory operands")
			if(base == Reg.NONE) {
				base = node.value
			} else if(index == Reg.NONE) {
				index = base
				base = node.value
				scale = 1
			} else {
				err(node.srcPos, "Too many registers")
			}
			return 0
		}

		if(node is RefNode)
			return node.intSupplier?.invoke() ?: err(node.srcPos, "Invalid ref node")

		err(node.srcPos, "Invalid node: $node")
	}



	// Encoding writing



	private fun byte(value: Int) = writer.i8(value)

	private fun word(value: Int) = writer.i16(value)

	private fun i24(value: Int) = writer.i24(value)

	private fun dword(value: Int) = writer.i32(value)



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
		if(value != 0 || r.rex8 || b.rex8)
			if(r.noRex8 || b.noRex8)
				invalid("REX prefix not allowed here")
			else
				byte(0x40 or value)
	}

	/** Add one if width is not BYTE and if widths has BYTE set */
	private fun writeOpcode(opcode: Int, mask: Int, width: Int) {
		val addition = (mask and 1) and (1 shl width).inv()
		if(opcode and 0xFF00 != 0) word(opcode + (addition shl 8)) else byte(opcode + addition)
	}

	private fun checkWidth(mask: Int, width: Int) {
		if((1 shl width) and mask == 0)
			invalid("Invalid operand width")
	}

	private fun writeO16(mask: Int, width: Int) {
		if(mask != 0b10 && width == 1) writer.i8(0x66)
	}



	private fun Any.rel(node: Node, width: Width) {
		relocs = 0
		val value = resolveRec(node, false)
		if(relocs != 0) {
			addLinkReloc(width, node, 0, true)
			writer.advance(width.bytes)
		} else if(!writer.writeWidth(width, value)) {
			err(node.srcPos, "Value out of range")
		}
	}



	private fun Any.imm64(node: Node, width: Width) {
		relocs = 0
		val value = resolveRec(node, false)
		if(relocs == 1) {
			if(width != Width.QWORD)
				err(node.srcPos, "Absolute relocations must occupy 64 bits")
			addAbsReloc(node)
			writer.advance(8)
		} else if(relocs != 0) {
			addLinkReloc(width, node, 0, false)
			writer.advance(width.bytes)
		} else if(!writer.writeWidth(width, value)) {
			err(node.srcPos, "Value out of range")
		}
	}



	private fun Any.imm(node: Node, width: Width) =
		imm64(node, if(width == Width.QWORD) Width.DWORD else width)



	private fun writeMem(node: OpNode, reg: Int, immWidth: Width) {
		val hasReloc = relocs > 0
		val hasIndex = index != Reg.NONE
		val hasBase = base != Reg.NONE

		fun swapRegs() {
			val temp = index
			index = base
			base = temp
		}

		// Index cannot be ESP/RSP, swap to base if possible
		if(hasIndex && index.isInvalidIndex) {
			when {
				scale != 1 -> err(node.srcPos, "Index cannot be ESP/RSP")
				hasBase    -> swapRegs()
				else       -> { base = index; index = Reg.NONE }
			}
		} else if(hasIndex && base.value == 5 && scale == 1 && index.value != 5) {
			swapRegs()
		}

		fun scaleErr(): Nothing = err(node.srcPos, "Invalid memory operand scale")

		// 1: [R*1] -> [R], avoid SIB
		// 2: [R*2] -> [R+R*1], avoid index-only SIB which produces DISP32 of zero
		// 3: [R*3] -> [R+R*2], [R+R*3] -> invalid
		// 5: [R*5] -> [R+R*4], [R+R*5] -> invalid
		when(scale) {
			0 -> index = Reg.NONE
			1 -> if(!hasBase) { base = index; index = Reg.NONE }
			2 -> if(!hasBase) { scale = 1; base = index }
			3 -> if(!hasBase) { scale = 2; base = index } else scaleErr()
			4 -> { }
			5 -> if(!hasBase) { scale = 4; base = index } else scaleErr()
			6 -> scaleErr()
			7 -> scaleErr()
			8 -> { }
			else -> scaleErr()
		}

		fun reloc(mod: Int) {
			when {
				hasReloc -> { addLinkReloc(Width.DWORD, node.child!!, 0, false); writer.i32(0) }
				mod == 1 -> writer.i8(disp.toInt())
				mod == 2 -> writer.i32(disp.toInt())
			}
		}

		val mod = when {
			hasReloc     -> 2 // disp32, can't be sure of size
			disp == 0L   -> 0
			disp.isImm8  -> 1
			disp.isImm32 -> 2
			else         -> invalid("Memory operand displacement out of range")
		}

		val s = scale.countTrailingZeroBits()
		val i = index.value
		val b = base.value

		if(hasIndex) { // SIB: [R*S] or [R*S+DISP] or [R+R*S] or [R+R*S+DISP]
			if(hasBase) {
				if(b == 5 && mod == 0) { // [RBP/R13+R*S] -> [RBP+R*S+DISP8]
					i24(0b01_000_100 or (reg shl 3) or (s shl 14) or (i shl 11) or (0b101 shl 8))
				} else {
					word((mod shl 6) or (reg shl 3) or 0b100 or (s shl 14) or (i shl 11) or (b shl 8))
					reloc(mod)
				}
			} else { // Index only, requires disp32
				word((reg shl 3) or 0b100 or (s shl 14) or (i shl 11) or (0b101 shl 8))
				reloc(0b10)
			}
		} else if(hasBase) { // Indirect: [R] or [R+DISP]
			if(b == 4) { // [RSP/R12] -> [RSP/R12+NONE*1] (same with DISP)
				word((mod shl 6) or (reg shl 3) or 0b100 or (0b00_100_100 shl 8))
				reloc(mod)
			} else if(b == 5 && mod == 0) { // [RBP/R13] -> [RBP/R13+0]
				word(0b00000000_01_000_101 or (reg shl 3))
			} else {
				byte((mod shl 6) or (reg shl 3) or b)
				reloc(mod)
			}
		} else if(relocs and 1 == 1) { // RIP-relative (odd reloc count)
			// immWidth can be QWORD
			byte((reg shl 3) or 0b101)
			addLinkReloc(Width.DWORD, node.child!!, immWidth.bytes.coerceAtMost(4), true)
			dword(0)
		} else { // Absolute 32-bit or empty memory operand
			word(0b00_100_101_00_000_100 or (reg shl 3))
			reloc(0b10)
		}
	}



	// Compound encodings



	private fun encode1R(opcode: Int, mask: Int, ext: Int, op1: Reg) {
		val width = op1.width.ordinal - 1
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(mask, width, Reg.NONE, Reg.NONE, op1)
		writeOpcode(opcode, mask, width)
		byte(0b11_000_000 or (ext shl 3) or op1.value)
	}


	private fun encode1O(opcode: Int, mask: Int, op1: Reg) {
		val width = op1.width.ordinal - 1
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(mask, width, Reg.NONE, Reg.NONE, op1)
		byte(opcode + (((mask and 1) and (1 shl width).inv()) shl 3) + op1.value)
	}

	private fun encode1M(opcode: Int, mask: Int, ext: Int, op1: OpNode, immWidth: Width) {
		val width = op1.width.ordinal - 1
		resolveMem(op1)
		if(mask.countOneBits() != 1) checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(mask, width, Reg.NONE, index, base)
		writeOpcode(opcode, mask, width)
		writeMem(op1, ext, immWidth)
	}

	private fun encode2RMMismatch(opcode: Int, mask: Int, op1: Reg, op2: OpNode, immWidth: Width) {
		val width = op1.width.ordinal - 1
		resolveMem(op2)
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(mask, width, op1, index, base)
		writeOpcode(opcode, mask, width)
		writeMem(op2, op1.value, immWidth)
	}

	private fun encode2RM(opcode: Int, mask: Int, op1: Reg, op2: OpNode, immWidth: Width) {
		if(op2.width != Width.NONE && op2.width != op1.width) invalid("Width mismatch")
		encode2RMMismatch(opcode, mask, op1, op2, immWidth)
	}

	private fun encode2RRMismatch(opcode: Int, mask: Int, op1: Reg, op2: Reg) {
		val width = op1.width.ordinal - 1
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(mask, width, op1, Reg.NONE, op2)
		writeOpcode(opcode, mask, width)
		byte(0b11_000_000 or (op1.value shl 3) or (op2.value))
	}

	private fun encode2RR(opcode: Int, mask: Int, op1: Reg, op2: Reg) {
		if(op1.type != op2.type) invalid("Width mismatch")
		encode2RRMismatch(opcode, mask, op1, op2)
	}

	private fun encode1RM(opcode: Int, mask: Int, ext: Int, op1: OpNode, immWidth: Width) {
		if(op1.type == OpType.MEM)
			encode1M(opcode, mask, ext, op1, immWidth)
		else
			encode1R(opcode, mask, ext, op1.reg)
	}

	private fun encode2RRM(opcode: Int, mask: Int, op1: Reg, op2: OpNode, immWidth: Width) {
		if(op2.type == OpType.MEM)
			encode2RM(opcode, mask, op1, op2, immWidth)
		else
			encode2RR(opcode, mask, op1, op2.reg)
	}

	private fun encode2RMR(opcode: Int, mask: Int, op1: OpNode, op2: Reg, immWidth: Width) {
		if(op1.type == OpType.MEM)
			encode2RM(opcode, mask, op2, op1, immWidth)
		else
			encode2RR(opcode, mask, op2, op1.reg)
	}



	// Assembly



	private fun handleDllCall(node: DllCallNode) {
		node.importPos = context.getDllImport(node.dllName.string, node.name.string)
		word(0b00_010_101_11111111)
		addLinkReloc(Width.DWORD, node, 0, true)
		dword(0)
	}



	private fun assembleIns(ins: InsNode) {
		currentIns = ins
		when {
			ins.op1 == null -> assemble0(ins.mnemonic)
			ins.op2 == null -> assemble1(ins.mnemonic, ins.op1)
			ins.op3 == null -> assemble2(ins.mnemonic, ins.op1, ins.op2)
			else            -> assemble3(ins.mnemonic, ins.op1, ins.op2, ins.op3)
		}
	}



	private fun assemble3(mnemonic: Mnemonic, op1: OpNode, op2: OpNode, op3: OpNode) { when(mnemonic) {
		Mnemonic.IMUL -> {
			if(op3.type != OpType.IMM) invalid()
			encode2RRM(0x69, 0b1110, op1.reg, op2, op1.width).imm(op3, op1.width)
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



	private fun encodeJCC(offset: Int, op1: OpNode) {
		word(0x800F or (offset shl 8))
		rel(op1, Width.DWORD)
	}



	private fun assemble1(mnemonic: Mnemonic, op1: OpNode) { when(mnemonic) {
		Mnemonic.JO -> encodeJCC(0, op1)
		Mnemonic.JNO -> encodeJCC(1, op1)
		Mnemonic.JB, Mnemonic.JNAE, Mnemonic.JC -> encodeJCC(2, op1)
		Mnemonic.JNB, Mnemonic.JAE, Mnemonic.JNC -> encodeJCC(3, op1)
		Mnemonic.JZ, Mnemonic.JE   -> encodeJCC(4, op1)
		Mnemonic.JNZ, Mnemonic.JNE  -> encodeJCC(5, op1)
		Mnemonic.JBE, Mnemonic.JNA -> encodeJCC(6, op1)
		Mnemonic.JNBE, Mnemonic.JA -> encodeJCC(7, op1)
		Mnemonic.JS -> encodeJCC(8, op1)
		Mnemonic.JNS -> encodeJCC(9, op1)
		Mnemonic.JP, Mnemonic.JPE -> encodeJCC(10, op1)
		Mnemonic.JNP, Mnemonic.JPO -> encodeJCC(11, op1)
		Mnemonic.JL, Mnemonic.JNGE -> encodeJCC(12, op1)
		Mnemonic.JNL, Mnemonic.JGE -> encodeJCC(13, op1)
		Mnemonic.JLE, Mnemonic.JNG -> encodeJCC(14, op1)
		Mnemonic.JNLE, Mnemonic.JG -> encodeJCC(15, op1)

		Mnemonic.INC -> encode1RM(0xFE, 0b1111, 0, op1, Width.NONE)
		Mnemonic.DEC -> encode1RM(0xFE, 0b1111, 1, op1, Width.NONE)

		Mnemonic.INT -> {
			if(op1.type != OpType.IMM) invalid()
			byte(0xCD).imm(op1, Width.BYTE)
		}

		Mnemonic.CALL ->
			if(op1.type == OpType.IMM)
				byte(0xE8).rel(op1, Width.DWORD)
			else
				encode1RM(0xFF, 0b1000, 2, op1, Width.NONE)

		Mnemonic.JMP ->
			if(op1.type == OpType.IMM)
				byte(0xE9).rel(op1, Width.DWORD)
			else
				encode1RM(0xFF, 0b1000, 4, op1, Width.NONE)

		Mnemonic.PUSH -> {
			if(op1.type == OpType.IMM) {
				byte(0x68).imm(op1, Width.DWORD)
			} else if(op1.type == OpType.MEM) {
				encode1M(0xFF, 0b1010, 6, op1, Width.NONE)
			} else {
				encode1O(0x50, 0b1010, op1.reg)
			}
		}

		Mnemonic.POP -> {
			if (op1.type == OpType.MEM)
				encode1M(0x8F, 0b1010, 0, op1, Width.NONE)
			else
				encode1O(0x58, 0b1010, op1.reg)
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
		Mnemonic.IMUL ->
			if(op2.type == OpType.IMM)
				encode2RR(0x69, 0b1110, op1.reg, op1.reg).imm(op2, op1.width)
			else
				encode2RRM(0xAF0F, 0b1110, op1.reg, op2, Width.NONE)

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

		Mnemonic.LEA -> encode2RM(0x8D, 0b1110, op1.reg, op2, Width.NONE)

		Mnemonic.MOV -> {
			if(op2.type == OpType.IMM) {
				if(op1.type == OpType.MEM)
					encode1RM(0xC6, 0b1111, 0, op1, op1.width).imm(op2, op1.width)
				else
					encode1O(0xB0, 0b1111, op1.reg).imm64(op2, op1.width)
			} else if(op2.type == OpType.MEM) {
				encode2RM(0x8A, 0b1111, op1.reg, op2, Width.NONE)
			} else {
				encode2RMR(0x88, 0b1111, op1, op2.reg, Width.NONE)
			}
		}

		Mnemonic.MOVSXD -> {
			if(op2.width != Width.DWORD)
				invalid()
			if(op2.type == OpType.MEM)
				encode2RMMismatch(0x63, 0b1100, op1.reg, op2, Width.NONE)
			else
				encode2RRMismatch(0x63, 0b1100, op1.reg, op2.reg)
		}

		Mnemonic.MOVSX -> {
			when(op2.width) {
				Width.BYTE -> if(op2.type == OpType.MEM)
					encode2RMMismatch(0xBE0F, 0b1110, op1.reg, op2, Width.NONE)
				else
					encode2RRMismatch(0xBE0F, 0b1110, op1.reg, op2.reg)
				Width.WORD -> if(op2.type == OpType.MEM)
					encode2RMMismatch(0xBF0F, 0b1100, op1.reg, op2, Width.NONE)
				else
					encode2RRMismatch(0xBF0F, 0b1100, op1.reg, op2.reg)
				else -> invalid()
			}
		}

		Mnemonic.MOVZX -> {
			when(op2.width) {
				Width.BYTE -> if(op2.type == OpType.MEM)
					encode2RMMismatch(0xB60F, 0b1110, op1.reg, op2, Width.NONE)
				else
					encode2RRMismatch(0xB60F, 0b1110, op1.reg, op2.reg)
				Width.WORD -> if(op2.type == OpType.MEM)
					encode2RMMismatch(0xB70F, 0b1100, op1.reg, op2, Width.NONE)
				else
					encode2RRMismatch(0xB70F, 0b1100, op1.reg, op2.reg)
				else -> invalid()
			}
		}

		Mnemonic.TEST -> {
			when(op2.type) {
				OpType.IMM -> encode1RM(0xF6, 0b1111, 0, op1, op1.width).imm(op2, op1.width)
				else -> encode2RMR(0x84, 0b1111, op1, op2.reg, Width.NONE)
			}
		}

		Mnemonic.XCHG ->
			if(op2.type == OpType.MEM)
				encode2RRM(0x86, 0b1111, op1.reg, op2, Width.NONE)
			else
				encode2RMR(0x86, 0b1111, op1, op2.reg, Width.NONE)

		Mnemonic.BT -> encodeBT(0xA30F, 4, op1, op2)
		Mnemonic.BTS -> encodeBT(0xAB0F, 5, op1, op2)
		Mnemonic.BTR -> encodeBT(0xB30F, 6, op1, op2)
		Mnemonic.BTC -> encodeBT(0xBB0F, 7, op1, op2)

		Mnemonic.POPCNT -> {
			byte(0xF3)
			encode2RRM(0xB80F, 0b1110, op1.reg, op2, Width.NONE)
		}

		Mnemonic.BSF -> encode2RRM(0xBC0F, 0b1110, op1.reg, op2, Width.NONE)
		Mnemonic.BSR -> encode2RRM(0xBD0F, 0b1110, op1.reg, op2, Width.NONE)

		Mnemonic.TZCNT -> {
			byte(0xF3)
			encode2RRM(0xBC0F, 0b1110, op1.reg, op2, Width.NONE)
		}

		Mnemonic.LZCNT -> {
			byte(0xF3)
			encode2RRM(0xBD0F, 0b1110, op1.reg, op2, Width.NONE)
		}

		Mnemonic.MOVBE -> when {
			op2.type == OpType.MEM -> encode2RM(0xF0380F, 0b1110, op1.reg, op2, Width.NONE)
			op1.type == OpType.MEM -> encode2RM(0xF1380F, 0b1110, op2.reg, op1, Width.NONE)
			else -> invalid()
		}

		else -> invalid()
	}}



	private fun encodeADD(opcode: Int, ext: Int, op1: OpNode, op2: OpNode) {
		when(op2.type) {
			OpType.IMM -> encode1RM(0x80, 0b1111, ext, op1, op1.width).imm(op2, op1.width)
			OpType.MEM -> encode2RM(opcode + 2, 0b1111, op1.reg, op2, Width.NONE)
			else       -> encode2RMR(opcode + 0, 0b1111, op1, op2.reg, Width.NONE)
		}
	}



	private fun encodeBT(opcode: Int, ext: Int, op1: OpNode, op2: OpNode) {
		if(op2.type == OpType.IMM)
			encode1RM(0xBA0F, 0b1110, ext, op1, Width.BYTE).imm(op2, Width.BYTE)
		else
			encode2RMR(opcode, 0b1110, op1, op2.reg, Width.NONE)
	}



	private fun encodeROL(ext: Int, op1: OpNode, op2: OpNode) {
		if(op2.reg == Reg.CL) {
			encode1RM(0xD2, 0b1111, ext, op1, Width.NONE)
		} else if(op2.type == OpType.IMM) {
			encode1RM(0xC0, 0b1111, 0, op1, Width.BYTE).imm(op2, Width.BYTE)
		} else {
			invalid()
		}
	}


}