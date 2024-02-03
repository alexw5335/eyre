package eyre

class Assembler(private val context: Context) {


	private var writer = context.textWriter

	private var section = context.textSec

	private lateinit var file: SrcFile

	private lateinit var currentIns: InsNode



	fun assemble() {
		for(file in context.files) {
			this.file = file
			file.nodes.forEach(::handleNode)
		}
	}



	private fun handleNode(node: Node) {
		try {
			when(node) {
				is ProcNode -> {
					node.sym.section = section
					node.sym.pos = writer.pos
					node.children.forEach(::handleNode)
				}
				//is InsNode -> assembleIns(node)
			}
		} catch(e: EyreError) {
			file.invalid = true
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
		fun sym(sym: Sym?): Long {
			if(sym == null) err(node.srcPos, "Unresolved symbol")
			if(sym is ConstSym) return sym.value
			if(sym is PosSym) {
				if(relocs++ == 0 && !regValid)
					err(node.srcPos, "First relocation (absolute or relative) must be positive and absolute")
				return 0
			}
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

		if(node is OpNode)   return resolveRec(node.child!!, regValid)
		if(node is IntNode)  return node.value
		if(node is UnNode)   return node.calc(regValid, ::resolveRec)
		if(node is NameNode) return sym(node.sym)

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



	private fun writeRel(node: Node, width: Width) {
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



	private fun writeMem(node: Node, reg: Int, immWidth: Width) {
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
				hasReloc -> { addLinkReloc(Width.DWORD, node, 0, false); writer.i32(0) }
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
			addLinkReloc(Width.DWORD, node, immWidth.bytes.coerceAtMost(4), true)
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
		checkWidth(mask, width)
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



	/*private fun assembleIns(ins: InsNode) {
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
		else -> invalid()
	}}



	private fun assemble1(mnemonic: Mnemonic, op1: OpNode) { when(mnemonic) {
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

		Mnemonic.NOT -> encode1RM(0xF6, 0b1111, 2, op1, Width.NONE)
		Mnemonic.NEG -> encode1RM(0xF6, 0b1111, 3, op1, Width.NONE)
		Mnemonic.MUL -> encode1RM(0xF6, 0b1111, 4, op1, Width.NONE)
		Mnemonic.IMUL -> encode1RM(0xF6, 0b1111, 5, op1, Width.NONE)
		Mnemonic.DIV -> encode1RM(0xF6, 0b1111, 6, op1, Width.NONE)
		Mnemonic.IDIV -> encode1RM(0xF6, 0b1111, 7, op1, Width.NONE)

		else -> invalid()
	}}



	private fun assemble2(mnemonic: Mnemonic, op1: OpNode, op2: OpNode) { when(mnemonic) {
		Mnemonic.IMUL -> encode2RRM(0xAF0F, 0b1111, op1.reg, op2, Width.NONE)

		Mnemonic.ADD -> encodeADD(0x00, 0, op1, op2)
		Mnemonic.OR  -> encodeADD(0x08, 1, op1, op2)
		Mnemonic.ADC -> encodeADD(0x10, 2, op1, op2)
		Mnemonic.SBB -> encodeADD(0x18, 3, op1, op2)
		Mnemonic.AND -> encodeADD(0x20, 4, op1, op2)
		Mnemonic.SUB -> encodeADD(0x28, 5, op1, op2)
		Mnemonic.XOR -> encodeADD(0x30, 6, op1, op2)
		Mnemonic.CMP -> encodeADD(0x38, 7, op1, op2)

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

		else -> invalid()
	}}



	private fun encodeADD(opcode: Int, ext: Int, op1: OpNode, op2: OpNode) {
		if(op2.type == OpType.IMM)
			encode1RM(0x80, 0b1111, ext, op1, op1.width).imm(op2, op1.width)
		else if(op2.type == OpType.MEM)
			encode2RM(opcode + 2, 0b1111, op1.reg, op2, Width.NONE)
		else
			encode2RMR(opcode + 0, 0b1111, op1, op2.reg, Width.NONE)
	}*/


}