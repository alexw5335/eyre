package eyre

class Assembler(private val context: CompilerContext) {


	private val textWriter = context.textWriter

	private var dataWriter = context.dataWriter

	private var writer = textWriter

	private var section = Section.TEXT

	private lateinit var group: EncodingGroup



	fun assemble() {
		for(srcFile in context.srcFiles) {
			for(node in srcFile.nodes) {
				when(node) {
					is InsNode   -> assembleInstruction(node)
					is LabelNode -> handleLabel(node)
					is VarNode   -> handleVar(node)
					else         -> { }
				}
			}
		}
	}



	// TODO: Add detailed error messages



	private fun invalidEncoding(): Nothing = error("Invalid encoding")



	// Node assembly



	private fun handleVar(node: VarNode) {
		dataWriter.align8()

		node.symbol.pos = dataWriter.pos
		for(part in node.parts) {
			for(value in part.nodes) {
				if(value is StringNode) {
					for(char in value.value.string)
						dataWriter.writeWidth(part.width, char.code)
				} else {
					dataWriter.writeWidth(part.width, resolveImm(value))
				}
			}
		}
	}



	private fun handleLabel(node: LabelNode) {
		node.symbol.pos = writer.pos
		if(node.symbol.name == StringInterner.MAIN) {
			if(context.entryPoint != null)
				error("Redeclaration of entry point")
			context.entryPoint = node.symbol
		}
	}



	// Instruction assembly



	private fun assembleInstruction(node: InsNode) {
		group = groups[node.mnemonic.ordinal]
		val customEncoding = customEncodings[node.mnemonic]

		if(customEncoding != null)
			customEncoding(node)
		else
			when(node.size) {
				0 -> encodeNone(Operands.NONE)
				1 -> assemble1(node)
				2 -> assemble2(node)
				3 -> invalidEncoding()
				4 -> invalidEncoding()
			}
	}



	private fun assemble0(node: InsNode) { when(node.mnemonic) {
		else -> invalidEncoding()
	}}



	private fun assemble1(node: InsNode) {
		when(val op1 = node.op1) {
			is RegNode -> encode1R(Operands.R, op1.value)
			is MemNode -> encode1M(Operands.M, op1, 0)
			else       -> invalidEncoding()
		}
	}



	private fun assemble2(node: InsNode) {
		val op1 = node.op1!!
		val op2 = node.op2!!

		if(op1 is RegNode) {
			if(op2 is RegNode) {
				if(op1.value.width != op2.value.width)
					invalidEncoding()
				else
					encode2RR(Operands.R_R, op1.value, op2.value)
			} else if(op2 is MemNode) {
				if(op2.width != null && op1.value.width != op2.width)
					invalidEncoding()
				else
					encode2RM(Operands.R_M, op1.value, op2, 0)
			} else {
				val imm = resolveImm(op2)
				encode1R(Operands.R_I, op1.value)
				writeImm(op2, op1.value.width, imm)
			}
		} else if(op1 is MemNode) {
			if(op2 is RegNode) {
				if(op1.width != null && op1.width != op2.value.width)
					invalidEncoding()
				else
					encode2RM(Operands.M_R, op2.value, op1, 0)
			} else {
				val imm = resolveImm(op2)
				val width = op1.width ?: invalidEncoding()
				encode1M(Operands.M_I, op1, width.immLength)
				writeImm(op2, width, imm)
			}
		} else {
			invalidEncoding()
		}
	}



	// Encoding utils



	private val Operands.encoding get(): Encoding {
		if(group.operandsBits and (1 shl ordinal) == 0)
			error("Encoding not present: $this")
		val index = (group.operandsBits and ((1 shl ordinal) - 1)).countOneBits()
		return group.encodings[index]
	}

	private fun writeRex(w: Int, r: Int, x: Int, b: Int) {
		val value = 0b0100_0000 or (w shl 3) or (r shl 2) or (x shl 1) or b
		if(value != 0b0100_0000) writer.i8(value)
	}

	private fun writeModRM(mod: Int, reg: Int, rm: Int) {
		writer.i8((mod shl 6) or (reg shl 3) or rm)
	}

	private fun writeSib(scale: Int, index: Int, base: Int) {
		writer.i8((scale shl 6) or (index shl 3) or base)
	}

	// return 1 if width is QWORD and widths has DWORD set, otherwise 0
	private fun rexW(width: Width, widths: Widths) =
		(width.bytes shr 3) and (widths.value shr 2)

	// Add one if width is not BYTE and if widths has BYTE set
	private fun getOpcode(encoding: Encoding, width: Width) =
		encoding.opcode + ((encoding.widths.value and 1) and (1 shl width.ordinal).inv())

	private fun writeOpcode(encoding: Encoding, width: Width) {
		writer.varLengthInt(getOpcode(encoding, width))
	}

	private fun checkO16(width: Width) {
		if(width == Width.WORD) writer.i8(0x66)
	}

	private fun checkWidths(width: Width, widths: Widths) {
		if(width !in widths) invalidEncoding()
	}

	private fun checkPrefix(encoding: Encoding) {
		if(encoding.prefix != 0) writer.i8(encoding.prefix)
	}

	private fun writeOpReg(opcode: Int, offset: Int) {
		val length = ((39 - (opcode or 1).countLeadingZeroBits()) and -8) shr 3
		writer.varLengthInt(opcode + (offset shl ((length - 1) shl 3)))
	}



	// Relocations



	private fun addDefaultRelocation(width: Width, node: AstNode) = context.relocations.add(Relocation(
		writer.pos,
		section,
		width,
		node,
		0,
		Relocation.Type.DEFAULT
	))

	private fun addAbsoluteRelocation(width: Width, node: AstNode) = context.relocations.add(Relocation(
		writer.pos,
		section,
		width,
		node,
		0,
		Relocation.Type.ABSOLUTE
	))

	private fun addRelativeRelocation(width: Width, node: AstNode, offset: Int) = context.relocations.add(Relocation(
		writer.pos,
		section,
		width,
		node,
		offset,
		Relocation.Type.RIP_RELATIVE
	))



	// Base encoding



	private fun encodeNone(operands: Operands) {
		val encoding = operands.encoding
		checkPrefix(encoding)
		writer.varLengthInt(encoding.opcode)
	}



	private fun encodeNone(operands: Operands, width: Width) {
		val encoding = operands.encoding
		checkPrefix(encoding)
		checkWidths(width, encoding.widths)
		checkO16(width)
		writeRex(rexW(width, encoding.widths), 0, 0, 0)
		writeOpcode(encoding, width)
	}



	private fun encode1R(operands: Operands, op1: Register) {
		val encoding = operands.encoding
		val width = op1.width
		checkWidths(width, encoding.widths)
		checkO16(width)
		checkPrefix(encoding)
		writeRex(rexW(width, encoding.widths), 0, 0, op1.rex)
		writeOpcode(encoding, width)
		writeModRM(0b11, encoding.extension, op1.value)
	}



	private fun encode1O(operands: Operands, op1: Register) {
		val encoding = operands.encoding
		val width = op1.width
		checkWidths(width, encoding.widths)
		checkO16(width)
		checkPrefix(encoding)
		writeRex(rexW(width, encoding.widths), 0, 0, op1.rex)
		val opcode = encoding.opcode +
			((encoding.widths.value and 1) and (1 shl width.ordinal).inv()) * 8
		writeOpReg(opcode, op1.value)
	}



	private fun encode1M(operands: Operands, op1: MemNode, immLength: Int) {
		val encoding = operands.encoding
		val width = op1.width ?: invalidEncoding()
		checkWidths(width, encoding.widths)
		checkO16(width)
		writeMem(
			getOpcode(encoding, width),
			op1.value,
			rexW(width, encoding.widths),
			0,
			encoding.extension,
			immLength
		)
	}



	// Note: Does NOT check op1 and op2 for equal widths, width is taken from op1
	private fun encode2RR(operands: Operands, op1: Register, op2: Register) {
		val encoding = operands.encoding
		val width = op1.width
		checkWidths(width, encoding.widths)
		checkO16(width)
		checkPrefix(encoding)
		writeRex(rexW(width, encoding.widths), op2.rex, 0, op1.rex)
		writeOpcode(encoding, width)
		writeModRM(0b11, op2.value, op1.value)
	}



	// Note: Does NOT check op1 and op2 for equal widths, width is taken from op1
	private fun encode2RM(operands: Operands, op1: Register, op2: MemNode, immLength: Int) {
		val encoding = operands.encoding
		val width = op1.width
		checkWidths(width, encoding.widths)
		checkO16(width)
		checkPrefix(encoding)
		writeMem(
			getOpcode(encoding, width),
			op2.value,
			rexW(width, encoding.widths),
			op1.rex,
			op1.value,
			immLength
		)
	}



	private fun encodeExact2RR(op1: Register, op2: Register) {
		if(op1.width != op2.width) invalidEncoding()
		encode2RR(Operands.R_R, op1, op2)
	}



	private fun encodeExact2RM(op1: Register, op2: MemNode) {
		if(op2.width != null && op1.width != op2.width) invalidEncoding()
		encode2RM(Operands.R_M, op1, op2, 0)
	}



	private fun encodeExact2MR(op1: MemNode, op2: Register) {
		if(op1.width != null && op1.width != op2.width) invalidEncoding()
		encode2RM(Operands.M_R, op2, op1, 0)
	}



	// Immediate resolution and writing



	private var immRelocCount = 0
	private val hasImmReloc get() = immRelocCount > 0



	private fun resolveImmRec(node: AstNode, regValid: Boolean): Long {
		if(node is IntNode) return node.value
		if(node is UnaryNode) return node.calculate(::resolveImmRec, regValid)
		if(node is BinaryNode) return node.calculate(::resolveImmRec, regValid)

		if(node is SymProviderNode) {
			return when(val symbol = node.symbol ?: error("Unresolved symbol")) {
				is PosSymbol ->
					if(immRelocCount++ == 0 && !regValid)
						error("First relocation (absolute or relative) must be positive and absolute")
					else
						0
				is IntSymbol       -> symbol.intValue
				else               -> error("Invalid symbol: $symbol")
			}
		}

		error("Invalid imm node: $node")
	}



	private fun resolveImm(node: AstNode): Long {
		immRelocCount = 0
		return resolveImmRec(node, true)
	}



	private fun writeImm(node: AstNode, width: Width) {
		writeImm(node, width, resolveImm(node))
	}



	private fun writeImm(node: AstNode, width: Width, value: Long, hasImm64: Boolean = false) {
		if(hasImmReloc) {
			val relocWidth = if(width == Width.QWORD && !hasImm64) Width.DWORD else width
			addDefaultRelocation(relocWidth, node)
			writer.advance(relocWidth.bytes)
			return
		}

		when {
			width == Width.BYTE -> {
				if(!value.isImm8) invalidEncoding()
				writer.i8(value.toInt())
			}

			width == Width.WORD -> {
				if(!value.isImm16) invalidEncoding()
				writer.i16(value.toInt())
			}

			width == Width.DWORD || !hasImm64 -> {
				if(!value.isImm32) invalidEncoding()
				writer.i32(value.toInt())
			}

			else -> writer.i64(value)
		}
	}



	// Memory resolution and writing



	private var baseReg: Register? = null
	private var indexReg: Register? = null
	private var indexScale = 0
	private var aso = -1 // -1: No reg present. 0: Both R64. 1: Both R32
	private var memRelocCount = 0
	private val hasMemReloc get() = memRelocCount > 0



	private fun checkAso(width: Width) {
		aso = if(width == Width.DWORD)
			if(aso == 0)
				error("Invalid effective address")
			else
				1
		else if(width == Width.QWORD)
			if(aso == 1)
				error("Invalid effective address")
			else
				0
		else
			error("Invalid effective address")
	}



	private fun resolveMemRec(node: AstNode, regValid: Boolean): Long {
		if(node is IntNode) return node.value
		if(node is UnaryNode) return node.calculate(::resolveMemRec, regValid)

		if(node is BinaryNode) {
			val regNode = node.left as? RegNode ?: node.right as? RegNode
			val intNode = node.left as? IntNode ?: node.right as? IntNode

			if(node.op == BinaryOp.MUL && regNode != null && intNode != null) {
				if(indexReg != null && !regValid) invalidEncoding()
				checkAso(regNode.value.width)
				indexReg = regNode.value
				indexScale = intNode.value.toInt()
				return 0
			}

			return node.calculate(::resolveMemRec, regValid)
		}

		if(node is SymProviderNode) {
			return when(val symbol = node.symbol ?: error("Unresolved symbol")) {
				is PosSymbol ->
					if(memRelocCount++ == 0 && !regValid)
						error("First relocation (absolute or relative) must be positive and absolute")
					else
						0
				is IntSymbol       -> symbol.intValue
				else               -> error("Invalid symbol: $symbol")
			}
		}

		if(node is RegNode) {
			if(!regValid) invalidEncoding()
			checkAso(node.value.width)

			if(baseReg != null) {
				if(indexReg != null)
					invalidEncoding()
				indexReg = node.value
				indexScale = 1
			} else {
				baseReg = node.value
			}

			return 0
		}

		error("Invalid mem node: $node")
	}



	private fun resolveMem(node: AstNode): Long {
		baseReg = null
		indexReg = null
		indexScale = 0
		aso = -1
		memRelocCount = 0

		val disp = resolveMemRec(node, true)

		// RSP and ESP cannot be index registers, swap to base if possible
		if(baseReg != null && indexReg != null && indexReg!!.isSP) {
			if(indexScale != 1) invalidEncoding()
			val temp = indexReg
			indexReg = baseReg
			baseReg = temp
		}

		when(indexScale) {
			0, 1, 2, 4, 8 -> { }
			else -> error("Invalid index: $indexScale")
		}

		if(indexScale == 0) indexReg = null

		return disp
	}



	private fun relocAndDisp(mod: Int, disp: Long, node: AstNode) {
		if(hasMemReloc) {
			addDefaultRelocation(Width.DWORD, node)
			writer.i32(0)
		} else if(mod == 1) {
			writer.i8(disp.toInt())
		} else if(mod == 2) {
			writer.i32(disp.toInt())
		}
	}



	private fun writeMem(
		opcode: Int,
		node: AstNode,
		rexW: Int,
		rexR: Int,
		reg: Int,
		immLength: Int
	) {
		val disp = resolveMem(node)
		val base = baseReg
		val index = indexReg
		val scale = indexScale.countTrailingZeroBits()

		if(aso == 1) writer.i8(0x67)

		val mod = when {
			hasMemReloc -> 2 // disp32
			disp == 0L -> if(base != null && base.invalidBase)
				1 // disp8, rbp as base needs an empty offset
			else
				0 // no disp
			disp.isImm8 -> 1 // disp8
			else -> 2 // disp32
		}

		if(index != null) { // SIB
			if(base != null) {
				writeRex(rexW, rexR, index.rex, base.rex)
				writer.varLengthInt(opcode)
				writeModRM(mod, reg, 0b100)
				writeSib(scale, index.value, base.value)
				relocAndDisp(mod, disp, node)
			} else {
				writeRex(rexW, rexR, index.rex, 0)
				writer.varLengthInt(opcode)
				writeModRM(mod, reg, 0b100)
				writeSib(scale, index.value, 0b101)
				relocAndDisp(mod, disp, node)
			}
		} else if(base != null) { // Indirect
			writeRex(rexW, rexR, 0, base.rex)
			writer.varLengthInt(opcode)

			if(base.isSpOr12) {
				writeModRM(mod, reg, 0b100)
				writeSib(0, 0b100, 0b100)
			} else {
				writeModRM(mod, reg, base.value)
			}

			relocAndDisp(mod, disp, node)
		} else if(memRelocCount and 1 == 1) { // RIP-relative
			writeRex(rexW, rexR, 0, 0)
			writer.varLengthInt(opcode)
			writeModRM(0b00, reg, 0b101)
			addRelativeRelocation(Width.DWORD, node, immLength)
			writer.i32(0)
		} else if(mod != 0) { // Absolute 32-bit
			error("Absolute memory operands not yet supported")
			//writeRex(rexW, rexR, 0, 0);
			//writeVarLengthInt(opcode);
			//writeModRM(0b00, reg, 0b100);
			//writeSib(0b00, 0b100, 0b101);
			//relocAndDisp(mod, disp, node);
		} else {
			invalidEncoding() // Empty memory operand
		}
	}



	/*
	Custom encodings
	 */



	private fun InsNode.ensure0() { if(size != 0) invalidEncoding() }

	private val customEncodings = HashMap<Mnemonic, (InsNode) -> Unit>().also { map ->
		infix fun Mnemonic.custom(customEncoding: (InsNode) -> Unit) = map.put(this, customEncoding)

		Mnemonic.HLT custom { it.ensure0(); writer.i8(0xF4) }

		Mnemonic.MOV   custom ::customEncodeMOV

		Mnemonic.CALL  custom ::customEncodeCALL
		Mnemonic.JMP   custom ::customEncodeJMP

		Mnemonic.JECXZ custom ::customEncodeJRCXZ
		Mnemonic.JRCXZ custom ::customEncodeJRCXZ
		Mnemonic.JA    custom ::customEncodeJCC
		Mnemonic.JAE   custom ::customEncodeJCC
		Mnemonic.JB    custom ::customEncodeJCC
		Mnemonic.JBE   custom ::customEncodeJCC
		Mnemonic.JC    custom ::customEncodeJCC
		Mnemonic.JE    custom ::customEncodeJCC
		Mnemonic.JG    custom ::customEncodeJCC
		Mnemonic.JGE   custom ::customEncodeJCC
		Mnemonic.JL    custom ::customEncodeJCC
		Mnemonic.JLE   custom ::customEncodeJCC
		Mnemonic.JNA   custom ::customEncodeJCC
		Mnemonic.JNAE  custom ::customEncodeJCC
		Mnemonic.JNB   custom ::customEncodeJCC
		Mnemonic.JNBE  custom ::customEncodeJCC
		Mnemonic.JNC   custom ::customEncodeJCC
		Mnemonic.JNE   custom ::customEncodeJCC
		Mnemonic.JNG   custom ::customEncodeJCC
		Mnemonic.JNGE  custom ::customEncodeJCC
		Mnemonic.JNL   custom ::customEncodeJCC
		Mnemonic.JNLE  custom ::customEncodeJCC
		Mnemonic.JO    custom ::customEncodeJCC
		Mnemonic.JP    custom ::customEncodeJCC
		Mnemonic.JPE   custom ::customEncodeJCC
		Mnemonic.JPO   custom ::customEncodeJCC
		Mnemonic.JS    custom ::customEncodeJCC
		Mnemonic.JZ    custom ::customEncodeJCC

		Mnemonic.ROL   custom ::customEncodeROL
		Mnemonic.ROR   custom ::customEncodeROL
		Mnemonic.RCL   custom ::customEncodeROL
		Mnemonic.RCR   custom ::customEncodeROL
		Mnemonic.SHL   custom ::customEncodeROL
		Mnemonic.SHR   custom ::customEncodeROL
		Mnemonic.SAR   custom ::customEncodeROL
		Mnemonic.SAL   custom ::customEncodeROL

		Mnemonic.ADD   custom ::customEncodeADD
		Mnemonic.OR    custom ::customEncodeADD
		Mnemonic.CMP   custom ::customEncodeADD
		Mnemonic.XOR   custom ::customEncodeADD
		Mnemonic.SUB   custom ::customEncodeADD
		Mnemonic.ADC   custom ::customEncodeADD
		Mnemonic.SBB   custom ::customEncodeADD

		Mnemonic.PUSH   custom ::customEncodePUSH
		Mnemonic.PUSHW  custom ::customEncodePUSHW
		Mnemonic.POP    custom ::customEncodePOP
		Mnemonic.POPW   custom ::customEncodePOPW

		Mnemonic.MOVSX custom ::customEncodeMOVSX
		Mnemonic.MOVZX custom ::customEncodeMOVSX
		Mnemonic.MOVSXD custom ::customEncodeMOVSXD

		Mnemonic.RET custom ::customEncodeRET
		Mnemonic.RETF custom ::customEncodeRETF

		Mnemonic.INT custom ::customEncodeINT

		Mnemonic.IN custom ::customEncodeIN
		Mnemonic.OUT custom ::customEncodeOUT

		Mnemonic.XCHG custom ::customEncodeXCHG

		Mnemonic.IMUL custom ::customEncodeIMUL
		Mnemonic.TEST custom ::customEncodeTEST

		Mnemonic.LOOP custom ::customEncodeLOOP
		Mnemonic.LOOPE custom ::customEncodeLOOP
		Mnemonic.LOOPNE custom ::customEncodeLOOP
	}



	/**
	 *     A8    TEST  A_I   1111
	 *     F6/0  TEST  RM_I  1111
	 *     84    TEST  RM_R  1111
	 */
	private fun customEncodeTEST(node: InsNode) {
		if(node.size != 2) invalidEncoding()
		val op1 = node.op1!!
		val op2 = node.op2!!

		if(op1 is RegNode) {
			if(op2 is RegNode) {
				encodeExact2RR(op1.value, op2.value)
			} else if(op1.value.isA) {
				encodeNone(Operands.CUSTOM1, op1.width)
				writeImm(op2, op1.width)
			} else {
				encode1R(Operands.R_I, op1.value)
				writeImm(op2, op1.width)
			}
		} else if(op1 is MemNode) {
			if(op2 is RegNode) {
				encodeExact2MR(op1, op2.value)
			} else {
				val width = op1.width ?: invalidEncoding()
				encode1M(Operands.M_I, op1, width.bytes)
				writeImm(op2, width)
			}
		} else {
			invalidEncoding()
		}
	}



	/**
	 *     F6/5  IMUL  RM       1111
	 *     AF0F  IMUL  R_RM     0111
	 *     6B    IMUL  R_RM_I8  0111
	 *     69    IMUL  R_RM_I   0111
	 */
	private fun customEncodeIMUL(node: InsNode) {
		if(node.size == 1) {
			when(val op1 = node.op1) {
				is RegNode -> encode1R(Operands.R, op1.value)
				is MemNode -> encode1M(Operands.M, op1, 0)
				else       -> invalidEncoding()
			}
		} else if(node.size == 2) {
			val op1 = node.op1 as? RegNode ?: invalidEncoding()

			when(val op2 = node.op2) {
				is RegNode -> encodeExact2RR(op1.value, op2.value)
				is MemNode -> encodeExact2RM(op1.value, op2)
				else       -> invalidEncoding()
			}
		} else if(node.size == 3) {
			val op1 = node.op1 as? RegNode ?: invalidEncoding()
			val op2 = node.op2!!
			val op3 = node.op3!!

			val imm = resolveImm(op3)

			if(op2 is RegNode) {
				if(op1.width != op2.width) invalidEncoding()
				if(imm.isImm8) {
					encode2RR(Operands.CUSTOM1, op1.value, op2.value)
					writeImm(op3, Width.BYTE, imm)
				} else {
					encode2RR(Operands.CUSTOM2, op1.value, op2.value)
					writeImm(op3, op1.width, imm)
				}
			} else if(op2 is MemNode) {
				if(op2.width != null && op2.width != op1.width) invalidEncoding()
				if(imm.isImm8) {
					encode2RM(Operands.CUSTOM1, op1.value, op2, 1)
					writeImm(op3, Width.BYTE, imm)
				} else {
					encode2RM(Operands.CUSTOM2, op1.value, op2, op1.width.bytes)
					writeImm(op3, op1.width, imm)
				}
			} else {
				invalidEncoding()
			}
		} else {
			invalidEncoding()
		}
	}



	/**
	 *     90  XCHG  A_O   0111
	 *     86  XCHG  RM_R  1111
	 *     86  XCHG  R_RM  1111
	 */
	private fun customEncodeXCHG(node: InsNode) {
		if(node.size != 2) invalidEncoding()
		val op1 = node.op1!!
		val op2 = node.op2!!

		when(op1) {
			is RegNode -> when(op2) {
				is RegNode -> when {
					op1.width != op2.width -> invalidEncoding()
					op1.value.isA          -> encode1O(Operands.CUSTOM1, op2.value)
					op2.value.isA          -> encode1O(Operands.CUSTOM1, op1.value)
					else                   -> encode2RR(Operands.R_R, op1.value, op2.value)
				}
				is MemNode -> encodeExact2RM(op1.value, op2)
				else       -> invalidEncoding()
			}
			is MemNode -> when(op2) {
				is RegNode -> encodeExact2MR(op1, op2.value)
				else       -> invalidEncoding()
			}
			else -> invalidEncoding()
		}
	}



	private fun customEncodeINT(node: InsNode) {
		if(node.size != 1) invalidEncoding()
		writer.i8(0xCD)
		writeImm(node.op1!!, Width.BYTE)
	}



	private fun customEncodeRET(node: InsNode) {
		when(node.size) {
			0    -> writer.i8(0xC3)
			1    -> { writer.i8(0xC2); writeImm(node.op1!!, Width.QWORD) }
			else -> invalidEncoding()
		}
	}



	private fun customEncodeRETF(node: InsNode) {
		when(node.size) {
			0    -> writer.i8(0xCB)
			1    -> { writer.i8(0xCA); writeImm(node.op1!!, Width.QWORD) }
			else -> invalidEncoding()
		}
	}



	/*
	MOV custom encodings
	 */



	private fun customEncodeMOVSXD(node: InsNode) {
		if(node.size != 2) invalidEncoding()
		val op1 = node.op1!!
		val op2 = node.op2!!

		if(op1 !is RegNode || op1.value.width != Width.QWORD) invalidEncoding()

		if(op2 is RegNode) {
			if(op1.width != Width.QWORD || op2.width != Width.DWORD)
				invalidEncoding()
			encode2RR(Operands.CUSTOM1, op1.value, op2.value)
		} else if(op2 is MemNode) {
			if(op1.width != Width.QWORD || op2.width != Width.DWORD)
				invalidEncoding()
			encode2RM(Operands.CUSTOM1, op1.value, op2, 0)
		} else {
			invalidEncoding()
		}
	}



	private fun customEncodeMOVSX(node: InsNode) {
		if(node.size != 2) invalidEncoding()
		val op1 = node.op1!!
		val op2 = node.op2!!

		if(op1 !is RegNode) invalidEncoding()

		if(op2 is RegNode) {
			when(op2.value.width) {
				Width.BYTE  -> encode2RR(Operands.CUSTOM1, op1.value, op2.value)
				Width.WORD  -> encode2RR(Operands.CUSTOM2, op1.value, op2.value)
				else        -> invalidEncoding()

			}
		} else if(op2 is MemNode) {
			when(op2.width ?: invalidEncoding()) {
				Width.BYTE  -> encode2RM(Operands.CUSTOM1, op1.value, op2, 0)
				Width.WORD  -> encode2RM(Operands.CUSTOM2, op1.value, op2, 0)
				else        -> invalidEncoding()
			}
		}
	}



	private fun customEncodeMOV(node: InsNode) {
		if(node.size != 2) invalidEncoding()
		val op1 = node.op1!!
		val op2 = node.op2!!

		if(op1 is RegNode) {
			if(op2 is RegNode) {
				encodeExact2RR(op1.value, op2.value)
			} else if(op2 is MemNode) {
				encodeExact2RM(op1.value, op2)
			} else {
				val imm = resolveImm(op2)
				encode1O(Operands.CUSTOM1, op1.value)
				writeImm(op2, op1.width, imm, true)
			}
		} else if(op1 is MemNode) {
			if(op2 is RegNode) {
				encodeExact2RM(op2.value, op1)
			} else {
				val width = op1.width ?: invalidEncoding()
				encode1M(Operands.M_I, op1, width.bytes)
				writeImm(op2, width)
			}
		} else {
			invalidEncoding()
		}
	}



	/*
	ADD/OR/CMP... ROL/ROR/SHL... custom encodings
	 */



	private fun customEncodeADD(node: InsNode) {
		if(node.size != 2) invalidEncoding()
		val op1 = node.op1!!
		val op2 = node.op2!!

		if(op1 is RegNode) {
			if(op2 is RegNode) {
				encodeExact2RR(op1.value, op2.value)
			} else if(op2 is MemNode) {
				encodeExact2RM(op1.value, op2)
			} else {
				val imm = resolveImm(op2)

				if(!hasImmReloc && imm.isImm8 && op1.value.width != Width.BYTE) {
					encode1R(Operands.CUSTOM2, op1.value)
					writeImm(op2, Width.BYTE, imm)
				} else if(op1.value.isA) {
					encodeNone(Operands.CUSTOM1, op1.value.width)
					writeImm(op2, op1.value.width, imm)
				} else {
					encode1R(Operands.R_I, op1.value)
					writeImm(op2, op1.value.width, imm)
				}
			}
		} else if(op1 is MemNode) {
			if(op2 is RegNode) {
				encodeExact2MR(op1, op2.value)
			} else if(op2 is MemNode) {
				invalidEncoding()
			} else {
				val width = op1.width ?: invalidEncoding()
				val imm = resolveImm(op2)

				if(!hasImmReloc && imm.isImm8 && width != Width.BYTE) {
					encode1M(Operands.CUSTOM2, op1, 1)
					writeImm(op2, Width.BYTE, imm)
				} else {
					encode1M(Operands.R_I, op1, width.bytes)
					writeImm(op2, width, imm)
				}
			}
		} else {
			invalidEncoding()
		}
	}



	private fun customEncodeROL(node: InsNode) {
		if(node.size != 2) invalidEncoding()
		val op1 = node.op1!!
		val op2 = node.op2!!

		if(op1 is RegNode) {
			if(op2 is RegNode) {
				if(op2.value != Register.CL) invalidEncoding()
				encode1R(Operands.CUSTOM3, op1.value)
			} else {
				val imm = resolveImm(op2)

				if(!hasImmReloc && imm == 1L)
					encode1R(Operands.CUSTOM2, op1.value)
				else {
					encode1R(Operands.CUSTOM1, op1.value)
					writeImm(op2, Width.BYTE, imm)
				}
			}
		} else if(op1 is MemNode) {
			if(op2 is RegNode) {
				if(op2.value != Register.CL) invalidEncoding()
				encode1M(Operands.CUSTOM3, op1, 0)
			} else {
				val imm = resolveImm(op2)

				if(!hasImmReloc && imm == 1L)
					encode1M(Operands.CUSTOM2, op1, 0)
				else {
					encode1M(Operands.CUSTOM1, op1, 1)
					writeImm(op2, Width.BYTE, imm)
				}
			}
		} else {
			invalidEncoding()
		}
	}



	/*
	REL custom encodings
	 */



	/**
	 * Note: REL32 operand must be the first custom operand.
	 */
	private fun encodeRel32(op1: AstNode) {
		val imm = resolveImm(op1)
		encodeNone(Operands.CUSTOM1)
		if(hasImmReloc) addRelativeRelocation(Width.DWORD, op1, 0)
		else if(!imm.isImm32) invalidEncoding()
		writer.i32(imm.toInt())
	}



	/**
	 * Note: REL8 operand must be the first custom operand.
	 */
	private fun encodeRel8(op1: AstNode) {
		val imm = resolveImm(op1)
		encodeNone(Operands.CUSTOM1)
		if(hasImmReloc) addRelativeRelocation(Width.BYTE, op1, 0)
		else if(!imm.isImm8) invalidEncoding()
		writer.i8(imm.toInt())
	}



	/**
	 * Node: REL8 must be the first custom operand and REL32 must be the second custom operand.
	 */
	private fun encodeRel8OrRel32(op1: AstNode) {
		val imm = resolveImm(op1)
		if(hasImmReloc) {
			encodeNone(Operands.CUSTOM2)
			addRelativeRelocation(Width.DWORD, op1, 0)
			writer.i32(0)
		} else if(imm.isImm8) {
			encodeNone(Operands.CUSTOM1)
			writer.i8(imm.toInt())
		} else if(imm.isImm32){
			encodeNone(Operands.CUSTOM2)
			writer.i32(imm.toInt())
		} else {
			invalidEncoding()
		}
	}



	/**
	 *     E2  LOOP    REL8
	 *     E1  LOOPE   REL8
	 *     E0  LOOPNE  REL8
	 */
	private fun customEncodeLOOP(node: InsNode) {
		if(node.size != 1) invalidEncoding()
		encodeRel8(node.op1!!)
	}



	/**
	 *     E367  JECXZ  REL8
	 *     E3    JRCXZ  REL8
	 */
	private fun customEncodeJRCXZ(node: InsNode) {
		if(node.size != 1) invalidEncoding()
		encodeRel8(node.op1!!)
	}



	/**
	 *     E8    CALL   REL32
	 *     FF/2  CALL   RM     0001
	 *     FF/3  CALLF  M      0111
	 */
	private fun customEncodeCALL(node: InsNode) {
		if(node.size != 1) invalidEncoding()

		when(val op1 = node.op1!!) {
			is RegNode -> encode1R(Operands.R, op1.value)
			is MemNode -> encode1M(Operands.M, op1, 0)
			else       ->
				if(op1 is SymProviderNode && op1.symbol is DllImportSymbol)
					encode1M(Operands.M, MemNode(Width.QWORD, op1), 0)
				else
					encodeRel32(op1)
		}
	}




	/**
	 *    EB    JMP   REL8
	 *    E9    JMP   REL32
	 *    FF/4  JMP   RM     0001
	 *    FF/5  JMPF  M      0111
	 */
	private fun customEncodeJMP(node: InsNode) {
		if(node.size != 1) invalidEncoding()

		when(val op1 = node.op1!!) {
			is RegNode -> encode1R(Operands.R, op1.value)
			is MemNode -> encode1M(Operands.M, op1, 0)
			else       -> encodeRel8OrRel32(op1)
		}
	}



	/**
	 *    77    JA    REL8
	 *    870F  JA    REL32
	 *    ...
	 */
	private fun customEncodeJCC(node: InsNode) {
		if(node.size != 1) invalidEncoding()
		encodeRel8OrRel32(node.op1!!)
	}



	/*
	IN/OUT custom encodings
	 */



	private fun encodeINOUT(op1: RegNode, op2: AstNode, opcode1: Int, opcode2: Int) {
		if(op2 is RegNode) {
			if(op2.value != Register.DX) invalidEncoding()
			when(op1.value) {
				Register.AL  -> writer.i8(opcode1)
				Register.AX  -> writer.i16(((opcode1 + 1) shl 8) or 0x66)
				Register.EAX -> writer.i8(opcode1 + 1)
				else         -> invalidEncoding()
			}
		} else {
			when(op1.value) {
				Register.AL  -> writer.i8(opcode2)
				Register.AX  -> writer.i16(((opcode2 + 1) shl 8) or 0x66)
				Register.EAX -> writer.i8(opcode2 + 1)
				else         -> invalidEncoding()
			}
			writeImm(op2, Width.BYTE)
		}
	}



	private fun customEncodeOUT(node: InsNode) {
		if(node.size != 2) invalidEncoding()
		encodeINOUT(node.op2 as? RegNode ?: invalidEncoding(), node.op1!!, 0xEE, 0xE6)
	}



	private fun customEncodeIN(node: InsNode) {
		if(node.size != 2) invalidEncoding()
		encodeINOUT(node.op1 as? RegNode ?: invalidEncoding(), node.op2!!, 0xEC, 0xE4)
	}



	/*
	PUSH/POP custom encodings
	 */



	/**
	 *     A00F    PUSH   FS
	 *     A80F    PUSH   GS
	 *     A00F66  PUSHW  FS
	 *     A80F66  PUSHW  GS
	 */
	private fun customEncodePUSHW(node: InsNode) {
		if(node.size != 1) invalidEncoding()
		when((node.op1 as? SegRegNode ?: invalidEncoding()).value) {
			SegReg.FS -> writer.varLengthInt(0xA00F66)
			SegReg.GS -> writer.varLengthInt(0xA80F66)
		}
	}



	/**
	 *     A10F    POP    FS
	 *     A90F    POP    GS
	 *     A10F66  POPW   FS
	 *     A90F66  POPW   GS
	 */
	private fun customEncodePOPW(node: InsNode) {
		if(node.size != 1) invalidEncoding()
		when((node.op1 as? SegRegNode ?: invalidEncoding()).value) {
			SegReg.FS -> writer.varLengthInt(0xA10F66)
			SegReg.GS -> writer.varLengthInt(0xA90F66)
		}
	}



	/**
	 *    FF/6  PUSH   M      0111
	 *    50    PUSH   O      0101
	 *    6A    PUSH   I8
	 *    6668  PUSH   I16
	 *    68    PUSH   I32
	 */
	private fun customEncodePUSH(node: InsNode) {
		if(node.size != 1) invalidEncoding()

		when(val op1 = node.op1!!) {
			is RegNode    -> encode1O(Operands.CUSTOM1, op1.value)
			is MemNode    -> encode1M(Operands.M, op1, 0)
			is SegRegNode -> when(op1.value) {
				SegReg.FS -> writer.i16(0xA00F)
				SegReg.GS -> writer.i16(0xA80F)
			}
			else -> {
				val imm = resolveImm(op1)

				if(hasImmReloc) {
					writer.i8(0x68)
					addDefaultRelocation(Width.DWORD, op1)
					writer.i32(0)
				} else if(imm.isImm8) {
					writer.i8(0x6A)
					writer.i8(imm.toInt())
				} else if(imm.isImm16) {
					writer.i16(0x6866)
					writer.i16(imm.toInt())
				} else {
					writer.i8(0x68)
					writer.i32(imm.toInt())
				}
			}
		}
	}



	/**
	 *    8F/0  POP    M      0111
	 *    58    POP    O      0101
	 */
	private fun customEncodePOP(node: InsNode) {
		if(node.size != 1) invalidEncoding()
		when(val op1 = node.op1!!) {
			is RegNode    -> encode1O(Operands.CUSTOM1, op1.value)
			is MemNode    -> encode1M(Operands.M, op1, 0)
			is SegRegNode -> when(op1.value) {
				SegReg.FS -> writer.i16(0xA10F)
				SegReg.GS -> writer.i16(0xA90F)
			}
			else -> invalidEncoding()
		}
	}


}