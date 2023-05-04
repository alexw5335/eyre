package eyre

import eyre.Mnemonic.*
import eyre.Width.*
import eyre.util.NativeWriter

class Assembler(private val context: CompilerContext) {


	private val textWriter = context.textWriter

	private val dataWriter = context.dataWriter

	private val epilogueWriter = NativeWriter()

	private var writer = textWriter

	private var section = Section.TEXT

	private var zeroOpcodePrefix = false



	private inline fun prefixed(prefix: Int, block: () -> Unit) {
		byte(prefix)
		block()
	}



	private inline fun withZeroOpcodePrefix(block: () -> Unit) {
		zeroOpcodePrefix = true
		block()
		zeroOpcodePrefix = false
	}



	private inline fun sectioned(writer: NativeWriter, section: Section, block: () -> Unit) {
		val prevWriter = this.writer
		val prevSection = this.section
		this.writer = writer
		this.section = section
		block()
		this.writer = prevWriter
		this.section = prevSection
	}



	fun assemble() {
		for(srcFile in context.srcFiles) {
			for(node in srcFile.nodes) {
				when(node) {
					is InsNode       -> handleInstruction(node)
					is LabelNode     -> handleLabel(node.symbol)
					is ProcNode      -> handleProc(node)
					is ScopeEndNode  -> handleScopeEnd(node)
					is VarResNode    -> handleVarRes(node)
					is VarDbNode     -> handleVarDb(node)
					is VarInitNode   -> handleVarInit(node)
					else             -> { }
				}
			}
		}

		for(s in context.stringLiterals) {
			sectioned(dataWriter, Section.DATA) {
				writer.align8()
				s.section = Section.DATA
				s.pos = writer.pos
				for(c in s.string) writer.i8(c.code)
				writer.i8(0)
			}
		}
	}



	private fun invalidEncoding(): Nothing = error("Invalid encoding")



	/*
	Node assembly
	 */



	private fun handleInstruction(node: InsNode) {
		node.prefix?.let { byte(it.value) }

		when {
			node.op1 == null -> assemble0(node)
			node.op2 == null -> assemble1(node, node.op1)
			node.op3 == null -> assemble2(node, node.op1, node.op2)
			node.op4 == null -> assemble3(node, node.op1, node.op2, node.op3)
			else             -> invalidEncoding()
		}
	}



	private fun handleVarRes(node: VarResNode) {
		val size = node.symbol.type.size
		context.bssSize = (context.bssSize + 7) and -8
		node.symbol.pos = context.bssSize
		context.bssSize += size
	}



	private fun handleVarDb(node: VarDbNode) {
		val prevWriter = writer
		writer = dataWriter
		writer.align8()

		node.symbol.pos = writer.pos

		for(part in node.parts) {
			for(value in part.nodes) {
				if(value is StringNode) {
					for(char in value.value)
						writer.writeWidth(part.width, char.code)
				} else {
					val imm = resolveImm(value)
					if(immRelocCount == 1) {
						if(part.width != QWORD)
							error("Absolute relocations must occupy 64 bits")
						writer.i64(0)
					} else {
						writer.writeWidth(part.width, imm)
					}
				}
			}
		}
		writer = prevWriter
	}



	@Suppress("CascadeIf")
	private fun writeInitialiser(node: AstNode, type: Type) {
		val start = writer.pos

		if(node is InitNode) {
			for(entry in node.entries) {
				writer.seek(start + entry.offset)
				writeInitialiser(entry.node, entry.type)
			}
			writer.seek(start + type.size)
		} else if(node is EqualsNode) {
			writeInitialiser(node.right, type)
		} else {
			val width = when(type.size) {
				1    -> BYTE
				2    -> WORD
				4    -> DWORD
				8    -> QWORD
				else -> error("Invalid initialiser: ${type.name}, of size: ${type.size}")
			}
			writeImm(node, width, true)
		}
	}



	private fun handleVarInit(node: VarInitNode) {
		sectioned(dataWriter, Section.DATA) {
			writer.align8()
			node.symbol.pos = writer.pos
			writeInitialiser(node.initialiser, node.symbol.type)
		}
	}



	private fun handleProc(node: ProcNode) {
		handleLabel(node.symbol)
		if(node.stackNodes.isEmpty()) return
		val registers = ArrayList<Register>()
		var toAlloc = 0
		var hasAlloc = false

		for(n in node.stackNodes) {
			if(n is RegNode) {
				registers.add(n.value)
			} else {
				hasAlloc = true
				toAlloc += resolveImm(n).toInt()
			}
		}

		if(hasAlloc) {
			if(toAlloc.isImm8) {
				writer.i32(0xEC_83_48 or (toAlloc shl 24))
			} else {
				writer.i24(0xEC_81_48)
				writer.i32(toAlloc)
			}
		}

		for(r in registers) {
			writeRex(0, 0, 0, r.rex)
			writer.i8(0x50 + r.value)
		}

		val prevWriter = writer
		writer = epilogueWriter

		for(i in registers.size - 1 downTo 0) {
			val r = registers[i]
			writeRex(0, 0, 0, r.rex)
			writer.i8(0x58 + r.value)
		}

		if(hasAlloc) {
			if(toAlloc.isImm8) {
				writer.i32(0xC4_83_48 or (toAlloc shl 24))
			} else {
				writer.i24(0xC4_81_48)
				writer.i32(toAlloc)
			}
		}

		writer.i8(0xC3)

		writer = prevWriter
	}



	private fun handleLabel(symbol: PosSymbol) {
		symbol.pos = writer.pos
		if(symbol.name == Names.MAIN) {
			if(context.entryPoint != null)
				error("Redeclaration of entry point")
			context.entryPoint = symbol
		}
	}



	private fun handleScopeEnd(node: ScopeEndNode) {
		if(node.symbol is ProcSymbol) {
			if(node.symbol.hasStackNodes) {
				encodeRETURN()
				epilogueWriter.reset()
			}

			node.symbol.size = writer.pos - node.symbol.pos
		}
	}



	/*
	Relocations
	 */



	private fun addLinkReloc(width: Width, node: AstNode) {
		context.relocs.add(Reloc(
			writer.pos,
			section,
			width,
			node,
			0,
			RelocType.LINK
		))
	}

	private fun addAbsReloc(node: AstNode) {
		context.relocs.add(Reloc(
			writer.pos,
			section,
			QWORD,
			node,
			0,
			RelocType.ABS
		))
		context.absRelocCount++
	}

	private fun addRelReloc(width: Width, node: AstNode, offset: Int) {
		context.relocs.add(Reloc(
			writer.pos,
			section,
			width,
			node,
			offset,
			RelocType.RIP
		))
	}



	/*
	Immediates
	 */



	private var immRelocCount = 0

	private val hasImmReloc get() = immRelocCount > 0

	private fun resolveImmSym(symbol: Symbol?, regValid: Boolean): Long {
		if(symbol is PosSymbol) {
			if(immRelocCount++ == 0 && !regValid)
				error("First relocation (absolute or relative) must be positive and absolute")
			else
				return 0
		}

		if(symbol is IntSymbol) return symbol.intValue

		invalidEncoding()
	}

	
	
	private fun resolveImmRec(node: AstNode, regValid: Boolean): Long = when(node) {
		is IntNode         -> node.value
		is UnaryNode       -> node.calculate(::resolveImmRec, regValid)
		is BinaryNode      -> node.calculate(::resolveImmRec, regValid)
		//is StringNode      -> node.value.ascii64()
		is SymNode         -> resolveImmSym(node.symbol, regValid)
		else               -> error("Invalid imm node: $node")
	}



	private fun resolveImm(node: AstNode): Long {
		immRelocCount = 0
		return resolveImmRec(node, true)
	}



	private fun writeImm(node: AstNode, width: Width, hasImm64: Boolean = false) {
		writeImm(node, width, resolveImm(node), hasImm64)
	}



	private fun writeImm(node: AstNode, width: Width, value: Long, hasImm64: Boolean = false) {
		val actualWidth = if(width == QWORD && !hasImm64) DWORD else width

		if(immRelocCount == 1) {
			if(!hasImm64 || width != QWORD)
				error("Absolute relocations are only allowed with 64-bit operands")
			addAbsReloc(node)
			writer.advance(8)
		} else if(hasImmReloc) {
			addLinkReloc(actualWidth, node)
			writer.advance(actualWidth.bytes)
		} else if(!writer.writeWidth(actualWidth, value)) {
			invalidEncoding()
		}
	}



	/*
	Memory operands
	 */



	private var baseReg: Register? = null
	private var indexReg: Register? = null
	private var indexScale = 0
	private var aso = Aso.NONE
	private var memRelocCount = 0
	private val hasMemReloc get() = memRelocCount > 0

	private enum class Aso { NONE, R64, R32 }



	private fun checkAso(width: Width) {
		aso = if(width == DWORD)
			if(aso == Aso.R64)
				invalidEncoding()
			else
				Aso.R32
		else if(width == QWORD)
			if(aso == Aso.R32)
				invalidEncoding()
			else
				Aso.R64
		else
			invalidEncoding()
	}



	private fun resolveMemBinary(node: BinaryNode, regValid: Boolean): Long {
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



	private fun resolveMemReg(reg: Register, regValid: Boolean): Long {
		if(!regValid) invalidEncoding()
		checkAso(reg.width)

		if(baseReg != null) {
			if(indexReg != null)
				invalidEncoding()
			indexReg = reg
			indexScale = 1
		} else {
			baseReg = reg
		}

		return 0
	}



	private fun resolveMemSym(node: SymNode, regValid: Boolean): Long {
		val symbol = node.symbol ?: error("Unresolved symbol")

		if(symbol is PosSymbol) {
			if(memRelocCount++ == 0 && !regValid)
				error("First relocation (absolute or relative) must be positive and absolute")
			else
				return 0
		}

		if(symbol is IntSymbol) return symbol.intValue

		if(symbol is VarAliasSymbol)
			return resolveMemRec((symbol.node as VarAliasNode).value, regValid)

		if(symbol is AliasRefSymbol)
			return resolveMemRec(symbol.value, regValid) + symbol.offset

		invalidEncoding()
	}



	private fun resolveMemRec(node: AstNode, regValid: Boolean): Long = when(node) {
		is IntNode    -> node.value
		is UnaryNode  -> node.calculate(::resolveMemRec, regValid)
		is BinaryNode -> resolveMemBinary(node, regValid)
		is RegNode    -> resolveMemReg(node.value, regValid)
		is SymNode    -> resolveMemSym(node, regValid)
		else          -> error("Invalid mem node: $node")
	}



	private fun resolveMem(node: AstNode): Long {
		baseReg = null
		indexReg = null
		indexScale = 0
		aso = Aso.NONE
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



	/*
	Writing
	 */



	private val OpNode.asReg get() = (this as? RegNode)?.value ?: invalidEncoding()

	private val OpNode.asMem get() = this as? MemNode ?: invalidEncoding()

	private val OpNode?.asFpu get() = (this!! as? FpuNode)?.value ?: invalidEncoding()

	private val OpNode.asXmm get() = (this as? XmmNode)?.value ?: invalidEncoding()

	private fun byte(value: Int) = writer.i8(value)

	private fun word(value: Int) = writer.i16(value)

	private fun dword(value: Int) = writer.i32(value)

	private fun checkWidths(widths: Widths, width: Width) {
		if(width !in widths) invalidEncoding()
	}

	private fun checkO16(width: Width) {
		if(width == WORD) writer.i8(0x66)
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

	/** Return 1 if width is QWORD and widths has DWORD set, otherwise 0 */
	private fun rexW(widths: Widths, width: Width) =
		(width.bytes shr 3) and (widths.value shr 2)

	/** Add one if width is not BYTE and if widths has BYTE set */
	private fun getOpcode(opcode: Int, widths: Widths, width: Width) =
		opcode + ((widths.value and 1) and (1 shl width.ordinal).inv())

	private fun writeOpcode(opcode: Int) {
		writer.varLengthInt(opcode)
		if(zeroOpcodePrefix) writer.i8(0)
	}

	private fun writeOpcode(opcode: Int, widths: Widths, width: Width) {
		writer.varLengthInt(getOpcode(opcode, widths, width))
		if(zeroOpcodePrefix) writer.i8(0)
	}



	private fun relocAndDisp(mod: Int, disp: Long, node: AstNode) {
		if(hasMemReloc) {
			addLinkReloc(DWORD, node)
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

		if(aso == Aso.R32) writer.i8(0x67)

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
				writeOpcode(opcode)
				writeModRM(mod, reg, 0b100)
				writeSib(scale, index.value, base.value)
				relocAndDisp(mod, disp, node)
			} else {
				writeRex(rexW, rexR, index.rex, 0)
				writeOpcode(opcode)
				writeModRM(mod, reg, 0b100)
				writeSib(scale, index.value, 0b101)
				relocAndDisp(mod, disp, node)
			}
		} else if(base != null) { // Indirect
			writeRex(rexW, rexR, 0, base.rex)
			writeOpcode(opcode)

			if(base.isSpOr12) {
				writeModRM(mod, reg, 0b100)
				writeSib(0, 0b100, 0b100)
			} else {
				writeModRM(mod, reg, base.value)
			}

			relocAndDisp(mod, disp, node)
		} else if(memRelocCount and 1 == 1) { // RIP-relative
			writeRex(rexW, rexR, 0, 0)
			writeOpcode(opcode)
			writeModRM(0b00, reg, 0b101)
			addRelReloc(DWORD, node, immLength)
			writer.i32(0)
		} else if(mod != 0) { // Absolute 32-bit
			error("Absolute memory operands not yet supported")
			//writeRex(rexW, rexR, 0, 0);
			//writeOpcode(opcode)
			//writeModRM(0b00, reg, 0b100);
			//writeSib(0b00, 0b100, 0b101);
			//relocAndDisp(mod, disp, node);
		} else {
			invalidEncoding() // Empty memory operand
		}
	}



	/*
	Base encoding
	 */



	private fun encodeNone(opcode: Int, widths: Widths, width: Width) {
		checkWidths(widths, width)
		checkO16(width)
		writeRex(rexW(widths, width), 0, 0, 0)
		writeOpcode(opcode, widths, width)
	}



	private fun encode1R(opcode: Int, widths: Widths, extension: Int, op1: Register) {
		val width = op1.width
		checkWidths(widths, width)
		checkO16(width)
		writeRex(rexW(widths, width), 0, 0, op1.rex)
		writeOpcode(opcode, widths, width)
		writeModRM(0b11, extension, op1.value)
	}



	private fun encode1R16(opcode: Int, extension: Int, op1: Register) {
		if(op1.width != WORD) invalidEncoding()
		writeRex(0, 0, 0, op1.rex)
		writeOpcode(opcode)
		writeModRM(0b11, extension, op1.value)
	}



	/**
	 * - [reversed] is false if the instruction has R_RM and RM_R, true otherwise.
	 * - [reversed] == true if: op1 in ModRM:reg, op2 in ModRM:r/m
	 * - [reversed] == false if: op1 in ModRM:r/m, op2 in ModRM:reg
	 *
	 */
	private fun encode2RR(
		opcode: Int,
		widths: Widths,
		op1: Register,
		op2: Register,
		reversed: Boolean,
		mismatch: Boolean = false,
	) {
		val width = op1.width
		if(!mismatch && op1.width != op2.width) invalidEncoding()
		checkWidths(widths, width)
		checkO16(width)

		if(reversed) {
			writeRex(rexW(widths, width), op1.rex, 0, op2.rex)
			writeOpcode(opcode, widths, width)
			writeModRM(0b11, op1.value, op2.value)
		} else {
			writeRex(rexW(widths, width), op2.rex, 0, op1.rex)
			writeOpcode(opcode, widths, width)
			writeModRM(0b11, op2.value, op1.value)
		}
	}



	private fun encode2XX(opcode: Int, op1: XmmReg, op2: XmmReg) {
		writeRex(0, op1.rex, 0, op2.rex)
		writeOpcode(opcode)
		writeModRM(0b11, op1.value, op2.value)
	}



	private fun encode2XM(opcode: Int, op1: XmmReg, op2: MemNode, memWidth: Width) {
		if(op2.width != null && op2.width != memWidth) invalidEncoding()
		writeMem(opcode, op2.value, 0, op1.rex, op1.value, 0)
	}



	private fun encode2RM(
		opcode: Int,
		widths: Widths,
		op1: Register,
		op2: MemNode,
		immLength: Int,
		mismatch: Boolean = false
	) {
		val width = op1.width
		if(!mismatch && op2.width != null && op2.width != op1.width) invalidEncoding()
		checkWidths(widths, width)
		checkO16(width)
		writeMem(
			getOpcode(opcode, widths, width),
			op2.value,
			rexW(widths, width),
			op1.rex,
			op1.value,
			immLength
		)
	}



	private fun encode1O(opcode: Int, widths: Widths, op1: Register) {
		val width = op1.width
		checkWidths(widths, width)
		checkO16(width)
		writeRex(rexW(widths, width), 0, 0, op1.rex)
		val finalOpcode = opcode + ((widths.value and 1) and (1 shl width.ordinal).inv()) * 8
		val length = ((39 - (finalOpcode or 1).countLeadingZeroBits()) and -8) shr 3
		writer.varLengthInt(finalOpcode + (op1.value shl ((length - 1) shl 3)))
	}



	private fun encode1MNoWidth(opcode: Int, extension: Int, op1: MemNode) {
		if(op1.width != null) invalidEncoding()
		writeMem(opcode, op1.value, 0, 0, extension, 0)
	}



	private fun encode1MAnyWidth(opcode: Int, extension: Int, op1: MemNode) {
		writeMem(opcode, op1.value, 0, 0, extension, 0)
	}



	private fun encode1MSingleWidth(opcode: Int, width: Width, extension: Int, op1: MemNode) {
		if(op1.width != null && op1.width != width) invalidEncoding()
		writeMem(
			opcode,
			op1.value,
			0,
			0,
			extension,
			0
		)
	}



	private fun encode1M(opcode: Int, widths: Widths, extension: Int, op1: MemNode, immLength: Int) {
		val width = op1.width ?: invalidEncoding()
		checkO16(width)
		writeMem(
			getOpcode(opcode, widths, width),
			op1.value,
			rexW(widths, width),
			0,
			extension,
			immLength
		)
	}



	/*
	Compound encoding
	 */



	private fun encode1RM(opcode: Int, widths: Widths, extension: Int, op1: OpNode) {
		when(op1) {
			is RegNode -> encode1R(opcode, widths, extension, op1.value)
			is MemNode -> encode1M(opcode, widths, extension, op1, 0)
			else       -> invalidEncoding()
		}
	}



	private fun encode1R16M16(opcode: Int, extension: Int, op1: OpNode) {
		when(op1) {
			is RegNode -> encode1R16(opcode, extension, op1.value)
			is MemNode -> encode1MSingleWidth(opcode, WORD, extension, op1)
			else       -> invalidEncoding()
		}
	}



	private fun encode1RM16(opcode: Int, widths: Widths, extension: Int, op1: OpNode) {
		when(op1) {
			is RegNode -> encode1R(opcode, widths, extension, op1.value)
			is MemNode -> encode1MSingleWidth(opcode, WORD, extension, op1)
			else       -> invalidEncoding()
		}
	}



	private fun encode2RRM(opcode: Int, widths: Widths, op1: Register, op2: OpNode) {
		when(op2) {
			is RegNode -> encode2RR(opcode, widths, op1, op2.value, reversed = true)
			is MemNode -> encode2RM(opcode, widths, op1, op2, 0)
			else -> invalidEncoding()
		}
	}



	/*
	Assembly
	 */



	private fun assemble0(node: InsNode) { when(node.mnemonic) {
		INSB   -> byte(0x6C)
		INSW   -> word(0x6D66)
		INSD   -> byte(0x6D)
		OUTSB  -> byte(0x6E)
		OUTSW  -> word(0x6F66)
		OUTSD  -> byte(0x6F)
		NOP    -> byte(0x90)
		CBW    -> word(0x9866)
		CWDE   -> byte(0x98)
		CDQE   -> word(0x9848)
		CWD    -> word(0x9966)
		CDQ    -> byte(0x99)
		CQO    -> word(0x9948)
		WAIT   -> byte(0x9B)
		FWAIT  -> byte(0x9B)
		PUSHF  -> word(0x9C66)
		PUSHFQ -> word(0x9C48)
		LAHF   -> byte(0x9F)
		MOVSB  -> byte(0xA4)
		MOVSW  -> word(0xA566)
		MOVSD  -> byte(0xA5)
		MOVSQ  -> word(0xA548)
		CMPSB  -> byte(0xA6)
		CMPSW  -> word(0xA766)
		CMPSD  -> byte(0xA7)
		CMPSQ  -> word(0xA748)
		STOSB  -> byte(0xAA)
		STOSW  -> word(0xAB66)
		STOSD  -> byte(0xAB)
		STOSQ  -> word(0xAB48)
		SCASB  -> byte(0xAE)
		SCASW  -> word(0xAF66)
		SCASD  -> byte(0xAF)
		SCASQ  -> word(0xAF48)
		LODSB  -> byte(0xAC)
		LODSW  -> word(0xAD66)
		LODSD  -> byte(0xAD)
		LODSQ  -> word(0xAD48)
		RET    -> byte(0xC3)
		RETF   -> byte(0xCB)
		LEAVE  -> byte(0xC9)
		INT3   -> byte(0xCC)
		INT1   -> byte(0xF1)
		IRETW  -> word(0xCF66)
		IRETD  -> byte(0xCF)
		IRETQ  -> word(0xCF48)
		HLT    -> byte(0xF4)
		CMC    -> byte(0xF5)
		CLC    -> byte(0xF8)
		STC    -> byte(0xF9)
		CLI    -> byte(0xFA)
		STI    -> byte(0xFB)
		CLD    -> byte(0xFC)
		STD    -> byte(0xFD)
		PAUSE  -> word(0x90F3)
		RETURN -> encodeRETURN()

		FADDP  -> word(0xC1DE)
		FMULP  -> word(0xC9DE)
		FCOM   -> word(0xD1D8)
		FCOMP  -> word(0xD9D8)
		FCOMPP -> word(0xD9DE)
		FSUBP  -> word(0xE9DE)
		FSUBRP -> word(0xE1DE)
		FDIVP  -> word(0xF9DE)
		FDIVRP -> word(0xF1DE)
		FNOP    -> word(0xD0D9)
		FXCH    -> word(0xC9D9)
		FCHS    -> word(0xE0D9)
		FABS    -> word(0xE1D9)
		FTST    -> word(0xE4D9)
		FXAM    -> word(0xE5D9)
		FLD1    -> word(0xE8D9)
		FLDL2T  -> word(0xE9D9)
		FLDL2E  -> word(0xEAD9)
		FLDPI   -> word(0xEBD9)
		FLDLG2  -> word(0xECD9)
		FLDLN2  -> word(0xEDD9)
		FLDZ    -> word(0xEED9)
		F2XM1   -> word(0xF0D9)
		FYL2X   -> word(0xF1D9)
		FPTAN   -> word(0xF2D9)
		FPATAN  -> word(0xF3D9)
		FXTRACT -> word(0xF4D9)
		FPREM1  -> word(0xF5D9)
		FDECSTP -> word(0xF6D9)
		FINCSTP -> word(0xF7D9)
		FPREM   -> word(0xF8D9)
		FYL2XP1 -> word(0xF9D9)
		FSQRT   -> word(0xFAD9)
		FSINCOS -> word(0xFBD9)
		FRNDINT -> word(0xFCD9)
		FSCALE  -> word(0xFDD9)
		FSIN    -> word(0xFED9)
		FCOS    -> word(0xFFD9)
		FUCOM   -> word(0xE1DD)
		FUCOMP  -> word(0xE9DD)
		FUCOMPP -> word(0xE9DA)
		FCLEX   -> writer.i24(0xE2DB9B)
		FNCLEX  -> word(0xE2DB)
		FINIT   -> writer.i24(0xE3DB9B)
		FNINIT  -> word(0xE3DB)

		VMCALL   -> writer.i24(0xC1010F)
		VMLAUNCH -> writer.i24(0xC2010F)
		VMRESUME -> writer.i24(0xC3010F)
		VMXOFF   -> writer.i24(0xC4010F)
		MONITOR  -> writer.i24(0xC8010F)
		MWAIT    -> writer.i24(0xC9010F)
		XGETBV   -> writer.i24(0xD0010F)
		XSETBV   -> writer.i24(0xD1010F)
		SWAPGS   -> writer.i24(0xF8010F)
		RDTSCP   -> writer.i24(0xF9010F)

		SYSCALLD -> writer.i16(0x050F)
		SYSCALLQ -> writer.i24(0x050F48)
		CLTS     -> writer.i16(0x060F)
		SYSRETD  -> writer.i16(0x070F)
		SYSRETQ  -> writer.i24(0x070F48)
		INVD     -> writer.i16(0x080F)
		WBINVD   -> writer.i16(0x090F)

		else   -> invalidEncoding()
	}}



	private fun assemble1(node: InsNode, op1: OpNode) { when(node.mnemonic) {
		PUSH   -> encodePUSH(node)
		POP    -> encodePOP(node)
		PUSHW  -> encodePUSHW(node)
		POPW   -> encodePOPW(node)
		NOT    -> encode1RM(0xF6, Widths.ALL, 2, op1)
		NEG    -> encode1RM(0xF6, Widths.ALL, 3, op1)
		MUL    -> encode1RM(0xF6, Widths.ALL, 4, op1)
		IMUL   -> encode1RM(0xF6, Widths.ALL, 5, op1)
		DIV    -> encode1RM(0xF6, Widths.ALL, 6, op1)
		IDIV   -> encode1RM(0xF6, Widths.ALL, 7, op1)
		INC    -> encode1RM(0xFE, Widths.ALL, 0, op1)
		DEC    -> encode1RM(0xFE, Widths.ALL, 1, op1)
		NOP    -> encode1RM(0x1F0F, Widths.NO864, 0, op1)
		RET    -> { byte(0xC2); writeImm(op1, QWORD) }
		RETF   -> { byte(0xCA); writeImm(op1, QWORD) }
		INT    -> { byte(0xCD); writeImm(op1, BYTE) }

		LOOP    -> encodeRel8(0xE2, op1)
		LOOPE   -> encodeRel8(0xE1, op1)
		LOOPNE  -> encodeRel8(0xE0, op1)
		JECXZ   -> { byte(0x67); encodeRel8(0xE3, op1) }
		JRCXZ   -> encodeRel8(0xE3, op1)
		CALL    -> encodeCALL(op1)
		CALLF   -> encode1M(0xFF, Widths.NO8, 3, op1.asMem, 0)
		JMP     -> encodeJMP(op1)
		JMPF    -> encode1M(0xFF, Widths.NO8, 5, op1.asMem, 0)
		DLLCALL -> encodeDLLCALL(node)
		
		JO     -> encodeJCC(0x70, node)
		JNO    -> encodeJCC(0x71, node)
		JB, JNAE, JC  -> encodeJCC(0x72, node)
		JNB, JAE, JNC -> encodeJCC(0x73, node)
		JZ, JE   -> encodeJCC(0x74, node)
		JNZ, JNE -> encodeJCC(0x75, node)
		JBE, JNA -> encodeJCC(0x76, node)
		JNBE, JA -> encodeJCC(0x77, node)
		JS       -> encodeJCC(0x78, node)
		JNS      -> encodeJCC(0x79, node)
		JP, JPE  -> encodeJCC(0x7A, node)
		JNP, JPO -> encodeJCC(0x7B, node)
		JL, JNGE -> encodeJCC(0x7C, node)
		JNL, JGE -> encodeJCC(0x7D, node)
		JLE, JNG -> encodeJCC(0x7E, node)
		JNLE, JG -> encodeJCC(0x7F, node)

		SETO     -> encodeSETCC(0x900F, node)
		SETNO    -> encodeSETCC(0x910F, node)
		SETB, SETNAE, SETC  -> encodeSETCC(0x920F, node)
		SETNB, SETAE, SETNC -> encodeSETCC(0x930F, node)
		SETZ, SETE   -> encodeSETCC(0x940F, node)
		SETNZ, SETNE -> encodeSETCC(0x950F, node)
		SETBE, SETNA -> encodeSETCC(0x960F, node)
		SETNBE, SETA -> encodeSETCC(0x970F, node)
		SETS         -> encodeSETCC(0x980F, node)
		SETNS        -> encodeSETCC(0x990F, node)
		SETP, SETPE  -> encodeSETCC(0x9A0F, node)
		SETNP, SETPO -> encodeSETCC(0x9B0F, node)
		SETL, SETNGE -> encodeSETCC(0x9C0F, node)
		SETNL, SETGE -> encodeSETCC(0x9D0F, node)
		SETLE, SETNG -> encodeSETCC(0x9E0F, node)
		SETNLE, SETG -> encodeSETCC(0x9F0F, node)

		FLD   -> encodeFLD(node)

		FADD  -> encodeFADD1(node, 0xC0D8, 0)
		FMUL  -> encodeFADD1(node, 0xC8D8, 1)
		FCOM  -> encodeFADD1(node, 0xD0D8, 2)
		FCOMP -> encodeFADD1(node, 0xD8D8, 3)
		FSUB  -> encodeFADD1(node, 0xE0D8, 4)
		FSUBR -> encodeFADD1(node, 0xE8D8, 5)
		FDIV  -> encodeFADD1(node, 0xF0D8, 6)
		FDIVR -> encodeFADD1(node, 0xF8D8, 7)

		FLDENV  -> encode1MNoWidth(0xD9, 4, op1.asMem)
		FLDCW   -> encode1MNoWidth(0xD9, 5, op1.asMem)
		FNSTENV -> encode1MNoWidth(0xD9, 6, op1.asMem)
		FNSTCW  -> encode1MNoWidth(0xD9, 7, op1.asMem)
		FSTCW   -> encode1MNoWidth(0xD99B, 7, op1.asMem)

		FSTSW -> encodeFSTSW(op1)
		FNSTSW -> encodeFNSTSW(op1)

		FIADD  -> encodeFIADD(node, 0)
		FIMUL  -> encodeFIADD(node, 1)
		FICOM  -> encodeFIADD(node, 2)
		FICOMP -> encodeFIADD(node, 3)
		FISUB  -> encodeFIADD(node, 4)
		FISUBR -> encodeFIADD(node, 5)
		FIDIV  -> encodeFIADD(node, 6)
		FIDIVR -> encodeFIADD(node, 7)

		FXCH -> encode1ST(0xC8D9, op1.asFpu)

		FST  -> encodeFST(node)
		FSTP -> encodeFSTP(node)

		FUCOM -> encode1ST(0xE0DD, op1.asFpu)
		FUCOMP -> encode1ST(0xE8DD, op1.asFpu)

		FILD   -> encodeFILD(node)
		FISTTP -> encodeFISTTP(node)
		FIST   -> encodeFIST(node)
		FISTP  -> encodeFISTP(node)

		FFREE -> encode1ST(0xC0DD, op1.asFpu)

		FRSTOR -> encode1MNoWidth(0xDD, 4, op1.asMem)
		FSAVE  -> encode1MNoWidth(0xDD9B, 6, op1.asMem)
		FNSAVE -> encode1MNoWidth(0xDD, 6, op1.asMem)
		FBSTP  -> encode1MNoWidth(0xDF, 6, op1.asMem)

		SLDT -> withZeroOpcodePrefix { encode1RM16(0x000F, Widths.NO8, 0, op1) }
		STR  -> withZeroOpcodePrefix { encode1RM16(0x000F, Widths.NO8, 1, op1) }
		LLDT -> withZeroOpcodePrefix { encode1R16M16(0x000F, 2, op1) }
		LTR  -> withZeroOpcodePrefix { encode1R16M16(0x000F, 3, op1) }
		VERR -> withZeroOpcodePrefix { encode1R16M16(0x000F, 4, op1) }
		VERW -> withZeroOpcodePrefix { encode1R16M16(0x000F, 5, op1) }
		SGDT -> encode1MNoWidth(0x010F, 0, op1.asMem)
		SIDT -> encode1MNoWidth(0x010F, 1, op1.asMem)
		LGDT -> encode1MNoWidth(0x010F, 2, op1.asMem)
		LIDT -> encode1MNoWidth(0x010F, 3, op1.asMem)
		SMSW -> encode1RM16(0x010F, Widths.NO8, 4, op1)
		LMSW -> encode1R16M16(0x010F, 6, op1)

		INVLPG -> encode1MNoWidth(0x010F, 7, op1.asMem)

		PREFETCHNTA -> encode1MSingleWidth(0x180F, BYTE, 0, op1.asMem)
		PREFETCHT0  -> encode1MSingleWidth(0x180F, BYTE, 1, op1.asMem)
		PREFETCHT1  -> encode1MSingleWidth(0x180F, BYTE, 2, op1.asMem)
		PREFETCHT2  -> encode1MSingleWidth(0x180F, BYTE, 3, op1.asMem)

		else -> invalidEncoding()
	}}



	private fun assemble2(node: InsNode, op1: OpNode, op2: OpNode) { when(node.mnemonic) {
		IMUL -> encode2RRM(0xAF0F, Widths.NO8, op1.asReg, op2)

		MOVSXD -> encodeMOVSXD(op1.asReg, op2)
		MOVSX  -> encodeMOVSX(0xBE0F, 0xBF0F, op1.asReg, op2)
		MOVZX  -> encodeMOVSX(0xB60F, 0xB70F, op1.asReg, op2)
		MOV    -> encodeMOV(op1, op2)
		IN     -> encodeINOUT(0xEC, 0xE4, op1.asReg, op2)
		OUT    -> encodeINOUT(0xEE, 0xE6, op2.asReg, op1)
		XCHG   -> encodeXCHG(op1, op2)
		TEST   -> encodeTEST(op1, op2)
		LEA    -> encode2RM(0x8D, Widths.NO8, op1.asReg, op2.asMem, 0, true)

		ADD -> encodeADD(0x00, 0, node)
		OR  -> encodeADD(0x08, 1, node)
		ADC -> encodeADD(0x10, 2, node)
		SBB -> encodeADD(0x18, 3, node)
		AND -> encodeADD(0x20, 4, node)
		SUB -> encodeADD(0x28, 5, node)
		XOR -> encodeADD(0x30, 6, node)
		CMP -> encodeADD(0x48, 7, node)

		ROL -> encodeROL(0, node)
		ROR -> encodeROL(1, node)
		RCL -> encodeROL(2, node)
		RCR -> encodeROL(3, node)
		SAL -> encodeROL(4, node)
		SHL -> encodeROL(4, node)
		SHR -> encodeROL(5, node)
		SAR -> encodeROL(7, node)

		CMOVO     -> encodeCMOVCC(0x400F, op1.asReg, op2)
		CMOVNO    -> encodeCMOVCC(0x410F, op1.asReg, op2)
		CMOVB, CMOVNAE, CMOVC  -> encodeCMOVCC(0x420F, op1.asReg, op2)
		CMOVNB, CMOVAE, CMOVNC -> encodeCMOVCC(0x430F, op1.asReg, op2)
		CMOVZ, CMOVE   -> encodeCMOVCC(0x440F, op1.asReg, op2)
		CMOVNZ, CMOVNE -> encodeCMOVCC(0x450F, op1.asReg, op2)
		CMOVBE, CMOVNA -> encodeCMOVCC(0x460F, op1.asReg, op2)
		CMOVNBE, CMOVA -> encodeCMOVCC(0x470F, op1.asReg, op2)
		CMOVS          -> encodeCMOVCC(0x480F, op1.asReg, op2)
		CMOVNS         -> encodeCMOVCC(0x440F, op1.asReg, op2)
		CMOVP, CMOVPE  -> encodeCMOVCC(0x4A0F, op1.asReg, op2)
		CMOVNP, CMOVPO -> encodeCMOVCC(0x4B0F, op1.asReg, op2)
		CMOVL, CMOVNGE -> encodeCMOVCC(0x4C0F, op1.asReg, op2)
		CMOVNL, CMOVGE -> encodeCMOVCC(0x4D0F, op1.asReg, op2)
		CMOVLE, CMOVNG -> encodeCMOVCC(0x4E0F, op1.asReg, op2)
		CMOVNLE, CMOVG -> encodeCMOVCC(0x4F0F, op1.asReg, op2)

		BSR -> encode2RRM(0xBD0F, Widths.NO8, op1.asReg, op2)
		BSF -> encode2RRM(0xBC0F, Widths.NO8, op1.asReg, op2)

		FADD  -> encode2STST(node, 0xC0D8, 0xC0DC)
		FMUL  -> encode2STST(node, 0xC8D8, 0xC8DC)
		FSUB  -> encode2STST(node, 0xE0D8, 0xE8DC)
		FSUBR -> encode2STST(node, 0xE8D8, 0xE0DC)
		FDIV  -> encode2STST(node, 0xF0D8, 0xF8DC)
		FDIVR -> encode2STST(node, 0xF8D8, 0xF0DC)

		FADDP  -> encode2STIST0(node, 0xC0DE)
		FMULP  -> encode2STIST0(node, 0xC8DE)
		FSUBP  -> encode2STIST0(node, 0xE8DE)
		FSUBRP -> encode2STIST0(node, 0xE0DE)
		FDIVP  -> encode2STIST0(node, 0xF8DE)
		FDIVRP -> encode2STIST0(node, 0xF0DE)

		FCMOVB   -> encodeFCMOVCC(0xC0DA, node)
		FCMOVE   -> encodeFCMOVCC(0xC8DA, node)
		FCMOVBE  -> encodeFCMOVCC(0xD0DA, node)
		FCMOVU   -> encodeFCMOVCC(0xD8DA, node)
		FCMOVNB  -> encodeFCMOVCC(0xC0DB, node)
		FCMOVNE  -> encodeFCMOVCC(0xC8DB, node)
		FCMOVNBE -> encodeFCMOVCC(0xD0DB, node)
		FCMOVNU  -> encodeFCMOVCC(0xD8DB, node)

		FCOMI   -> encode2ST0STI(node, 0xF0DB)
		FCOMIP  -> encode2ST0STI(node, 0xF0DF)
		FUCOMI  -> encode2ST0STI(node, 0xE8DB)
		FUCOMIP -> encode2ST0STI(node, 0xE8DF)

		LAR -> encodeLAR(op1, op2, 0x020F)
		LSL -> encodeLAR(op1, op2, 0x030F)

		MOVUPS   -> encode2XMXM(0x00, 0x100F, op1, op2, XWORD)
		MOVUPD   -> encode2XMXM(0x66, 0x100F, op1, op2, XWORD)
		MOVSD    -> encode2XMXM(0xF2, 0x100F, op1, op2, QWORD)
		MOVSS    -> encode2XMXM(0xF3, 0x100F, op1, op2, DWORD)
		MOVHLPS  -> encode2XX(0x120F, op1.asXmm, op2.asXmm)
		MOVLPS   -> encode2XMMX(0x00, 0x120F, op1, op2, QWORD)
		MOVLPD   -> encode2XMMX(0x66, 0x120F, op1, op2, QWORD)
		MOVDDUP  -> encode2XXM(0xF2, 0x120F, op1, op2, QWORD)
		MOVSLDUP -> encode2XXM(0xF3, 0x120F, op1, op2, XWORD)
		UNPCKLPS -> encode2XXM(0x00, 0x140F, op1, op2, XWORD)
		UNPCKLPD -> encode2XXM(0x66, 0x140F, op1, op2, XWORD)
		UNPCKHPS -> encode2XXM(0x00, 0x150F, op1, op2, XWORD)
		UNPCKHPD -> encode2XXM(0x66, 0x150F, op1, op2, XWORD)
		MOVLHPS  -> encode2XX(0x160F, op1.asXmm, op2.asXmm)
		MOVHPS   -> encode2XMMX(0x00, 0x160F, op1, op2, QWORD)
		MOVHPD   -> encode2XMMX(0x66, 0x160F, op1, op2, QWORD)
		MOVAPS   -> encode2XMXM(0x00, 0x280F, op1, op2, XWORD)
		MOVAPD   -> encode2XMXM(0x66, 0x280F, op1, op2, XWORD)

		else     -> invalidEncoding()
	}}



	private fun encode2XMMX(prefix: Int, opcode: Int, op1: OpNode, op2: OpNode, memWidth: Width) {
		if(prefix != 0) byte(prefix)
		when(op1) {
			is XmmNode -> when(op2) {
				is MemNode -> encode2XM(opcode, op1.value, op2, memWidth)
				else       -> invalidEncoding()
			}
			is MemNode -> when(op2) {
				is XmmNode -> encode2XM(opcode + (1 shl 8), op2.value, op1, memWidth)
				else       -> invalidEncoding()
			}
			else -> invalidEncoding()
		}
	}



	private fun encode2XMXM(prefix: Int, opcode: Int, op1: OpNode, op2: OpNode, memWidth: Width) {
		if(prefix != 0) byte(prefix)
		when(op1) {
			is XmmNode -> when(op2) {
				is XmmNode -> encode2XX(opcode, op1.value, op2.value)
				is MemNode -> encode2XM(opcode, op1.value, op2, memWidth)
				else       -> invalidEncoding()
			}
			is MemNode -> when(op2) {
				is XmmNode -> encode2XM(opcode + (1 shl 8), op2.value, op1, memWidth)
				else       -> invalidEncoding()
			}
			else -> invalidEncoding()
		}
	}



	private fun encode2XXM(prefix: Int, opcode: Int, op1: OpNode, op2: OpNode, memWidth: Width) {
		if(prefix != 0) byte(prefix)
		when(op1) {
			is XmmNode -> when(op2) {
				is XmmNode -> encode2XX(opcode, op1.value, op2.value)
				is MemNode -> encode2XM(opcode, op1.value, op2, memWidth)
				else       -> invalidEncoding()
			}
			else -> invalidEncoding()
		}
	}



	/*
		private fun encode2XXM(node: InsNode, opcode: Int, memWidth: Width) {
			val op1 = (node.op1 as? XmmNode)?.value ?: invalidEncoding()

			when(val op2 = node.op2!!) {
				is XmmNode -> encode2XX(opcode, op1, op2.value)
				is MemNode -> encode2XM(opcode, op1, op2, memWidth)
				else       -> invalidEncoding()
			}
		}



		private fun encode2XMX(node: InsNode, opcode: Int, memWidth: Width) {
			val op2 = (node.op2 as? XmmNode)?.value ?: invalidEncoding()

			when(val op1 = node.op1!!) {
				is XmmNode -> encode2XX(opcode, op1.value, op2)
				is MemNode -> encode2XM(opcode, op2, op1, memWidth)
				else       -> invalidEncoding()
			}
		}
	*/



	private fun assemble3(node: InsNode, op1: OpNode, op2: OpNode, op3: OpNode) { when(node.mnemonic) {
		IMUL -> encodeIMUL(op1.asReg, op2, op3)
		else -> invalidEncoding()
	}}



	/*
	Misc. encodings
	 */



	private fun encodeCMOVCC(opcode: Int, op1: Register, op2: OpNode) {
		encode2RRM(opcode, Widths.NO8, op1, op2)
	}
	
	
	
	private fun encodeSETCC(opcode: Int, node: InsNode) {
		encode1RM(opcode, Widths.ONLY8, 0, node.op1!!)
	}



	private fun encodeINOUT(opcode1: Int, opcode2: Int, op1: Register, op2: OpNode) {
		if(op2 is RegNode) {
			if(op2.value != Register.DX)
				invalidEncoding()
			when(op1) {
				Register.AL  -> byte(opcode1)
				Register.AX  -> word(((opcode1 + 1) shl 8) or 0x66)
				Register.EAX -> byte(opcode1 + 1)
				else         -> invalidEncoding()
			}
		} else {
			when(op1) {
				Register.AL  -> byte(opcode2)
				Register.AX  -> word(((opcode2 + 1) shl 8) or 0x66)
				Register.EAX -> byte(opcode2 + 1)
				else         -> invalidEncoding()
			}
			writeImm(op2, BYTE)
		}
	}



	/**
	 *     90  XCHG  A_O   0111
	 *     86  XCHG  R_RM  1111
	 */
	private fun encodeXCHG(op1: OpNode, op2: OpNode) {
		when(op1) {
			is RegNode -> when(op2) {
				is RegNode -> when {
					op1.width != op2.width -> invalidEncoding()
					op1.value.isA          -> encode1O(0x90, Widths.NO8, op2.value)
					op2.value.isA          -> encode1O(0x90, Widths.NO8, op1.value)
					else                   -> encode2RR(0x86, Widths.ALL, op1.value, op2.value, false)
				}
				is MemNode -> encode2RM(0x86, Widths.ALL, op1.value, op2, 0)
				else       -> invalidEncoding()
			}
			is MemNode -> when(op2) {
				is RegNode -> encode2RM(0x86, Widths.ALL, op2.value, op1, 0)
				else       -> invalidEncoding()
			}
			else -> invalidEncoding()
		}
	}



	/**
	 *     6B    IMUL  R_RM_I8  0111
	 *     69    IMUL  R_RM_I   0111
	 */
	private fun encodeIMUL(op1: Register, op2: OpNode, op3: OpNode) {
		val imm = resolveImm(op3)

		if(op2 is RegNode) {
			if(imm.isImm8) {
				encode2RR(0x6B, Widths.NO8, op1, op2.value, reversed = true)
				writeImm(op3, BYTE, imm)
			} else {
				encode2RR(0x69, Widths.NO8, op1, op2.value, reversed = true)
				writeImm(op3, op1.width, imm)
			}
		} else if(op2 is MemNode) {
			if(imm.isImm8) {
				encode2RM(0x6B, Widths.NO8, op1, op2, 1)
				writeImm(op3, BYTE, imm)
			} else {
				encode2RM(0x69, Widths.NO8, op1, op2, op1.width.bytes)
				writeImm(op3, op1.width, imm)
			}
		} else {
			invalidEncoding()
		}
	}



	/**
	 *     A8    TEST  A_I   1111
	 *     F6/0  TEST  RM_I  1111
	 *     84    TEST  RM_R  1111
	 */
	private fun encodeTEST(op1: OpNode, op2: OpNode) {
		if(op1 is RegNode) {
			if(op2 is RegNode) {
				encode2RR(0x84, Widths.ALL, op1.value, op2.value, false)
			} else if(op1.value.isA) {
				encodeNone(0xA8, Widths.ALL, op1.width)
				writeImm(op2, op1.width)
			} else {
				encode1R(0xF6, Widths.ALL, 0, op1.value)
				writeImm(op2, op1.width)
			}
		} else if(op1 is MemNode) {
			if(op2 is RegNode) {
				encode2RM(0x84, Widths.ALL, op2.value, op1, 0)
			} else {
				val width = op1.width ?: invalidEncoding()
				encode1M(0xF6, Widths.ALL, 0, op1, width.bytes)
				writeImm(op2, width)
			}
		} else {
			invalidEncoding()
		}
	}



	/*
	PUSH/POP encodings
	 */



	/**
	 *     FF/6  PUSH   M      0111
	 *     50    PUSH   O      0101
	 *     6A    PUSH   I8
	 *     68    PUSH   I
	 *     A00F  PUSH   FS
	 *     A80F  PUSH   GS
	 */
	private fun encodePUSH(node: InsNode) {
		when(val op1 = node.op1!!) {
			is RegNode -> encode1O(0x50, Widths.NO832, op1.value)
			is MemNode -> encode1M(0xFF, Widths.NO8, 6, op1, 0)
			is SegRegNode -> when(op1.value) {
				SegReg.FS -> word(0xA00F)
				SegReg.GS -> word(0xA80F)
			}
			else -> {
				val imm = resolveImm(op1)
				if(hasImmReloc) {
					byte(0x68)
					addLinkReloc(DWORD, op1)
					dword(0)
				} else if(imm.isImm8) {
					byte(0x6A)
					byte(imm.toInt())
				} else if(imm.isImm32) {
					byte(0x68)
					dword(imm.toInt())
				} else {
					invalidEncoding()
				}
			}
		}
	}



	/**
	 *    8F/0  POP  M   0111
	 *    58    POP  O   0101
	 *    A10F  POP  FS
	 *    A90F  POP  GS
	 */
	private fun encodePOP(node: InsNode) {
		when(val op1 = node.op1!!) {
			is RegNode -> encode1O(0x58, Widths.NO832, op1.value)
			is MemNode -> encode1M(0x8F, Widths.NO8, 0, op1, 0)
			is SegRegNode -> when(op1.value) {
				SegReg.FS -> word(0xA10F)
				SegReg.GS -> word(0xA90F)
			}
			else -> invalidEncoding()
		}
	}



	/**
	 *     A00F66  PUSHW  FS
	 *     A80F66  PUSHW  GS
	 */
	private fun encodePUSHW(node: InsNode) {
		when(val op1 = node.op1!!) {
			is SegRegNode -> when(op1.value) {
				SegReg.FS -> { byte(0x66); word(0xA00F) }
				SegReg.GS -> { byte(0x66); word(0xA80F) }
			}
			else -> invalidEncoding()
		}
	}



	/**
	 *     A10F66  POPW   FS
	 *     A90F66  POPW   GS
	 */
	private fun encodePOPW(node: InsNode) {
		when(val op1 = node.op1!!) {
			is SegRegNode -> when(op1.value) {
				SegReg.FS -> { byte(0x66); word(0xA10F) }
				SegReg.GS -> { byte(0x66); word(0xA90F) }
			}
			else -> invalidEncoding()
		}
	}



	/*
	REL encodings
	 */




	private fun encodeRel8(opcode: Int, op1: AstNode) {
		val imm = resolveImm(op1)
		byte(opcode)
		if(hasImmReloc) addRelReloc(BYTE, op1, 0)
		else if(!imm.isImm8) invalidEncoding()
		byte(imm.toInt())
	}



	private fun encodeRel8OrRel32(opcode1: Int, opcode2: Int, op1: AstNode) {
		val imm = resolveImm(op1)
		if(hasImmReloc) {
			writer.varLengthInt(opcode2)
			addRelReloc(DWORD, op1, 0)
			dword(0)
		} else if(imm.isImm8) {
			byte(opcode1)
			byte(imm.toInt())
		} else if(imm.isImm32){
			writer.varLengthInt(opcode2)
			dword(imm.toInt())
		} else {
			invalidEncoding()
		}
	}
	
	
	
	private fun encodeRel32(opcode: Int, op1: OpNode) {
		val imm = resolveImm(op1)
		byte(opcode)
		if(hasImmReloc) addRelReloc(DWORD, op1, 0)
		else if(!imm.isImm32) invalidEncoding()
		writer.i32(imm.toInt())
	}
	



	/**
	 *     E8    CALL   REL32
	 *     FF/2  CALL   RM     0001
	 *     FF/3  CALLF  M      0111
	 */
	private fun encodeCALL(op1: OpNode) {
		when(op1) {
			is RegNode -> encode1R(0xFF, Widths.ONLY64, 2, op1.value)
			is MemNode -> encode1M(0xFF, Widths.ONLY64, 2, op1, 0)
			else       -> encodeRel32(0xE8, op1)
		}
	}




	/**
	 *    EB    JMP   REL8
	 *    E9    JMP   REL32
	 *    FF/4  JMP   RM     0001
	 *    FF/5  JMPF  M      0111
	 */
	private fun encodeJMP(op1: OpNode) {
		when(op1) {
			is RegNode -> encode1R(0xFF, Widths.ONLY64, 4, op1.value)
			is MemNode -> encode1M(0xFF, Widths.ONLY64, 4, op1, 0)
			else       -> encodeRel8OrRel32(0xEB, 0xE9, op1)
		}
	}

	

	private fun encodeJCC(opcode: Int, node: InsNode) {
		encodeRel8OrRel32(opcode, ((opcode + 0x10) shl 8) or 0x0F, node.op1!!)
	}



	/*
	ADD/OR/ADC/SBB/AND/SUB/XOR/CMP encodings
	 */



	private fun encodeADD(start: Int, extension: Int, node: InsNode) {
		val op1 = node.op1!!
		val op2 = node.op2!!

		if(op1 is RegNode) {
			if(op2 is RegNode) {
				encode2RR(start + 0, Widths.ALL, op1.value, op2.value, reversed = false)
			} else if(op2 is MemNode) {
				encode2RM(start + 2, Widths.ALL, op1.value, op2, 0)
			} else {
				val imm = resolveImm(op2)

				if(!hasImmReloc && imm.isImm8 && op1.value.width != BYTE) {
					encode1R(0x83, Widths.NO8, extension, op1.value)
					writeImm(op2, BYTE, imm)
				} else if(op1.value.isA) {
					encodeNone(start + 4, Widths.ALL, op1.value.width)
					writeImm(op2, op1.value.width, imm)
				} else {
					encode1R(0x80, Widths.ALL, extension, op1.value)
					writeImm(op2, op1.value.width, imm)
				}
			}
		} else if(op1 is MemNode) {
			if(op2 is RegNode) {
				encode2RM(start + 0, Widths.ALL, op2.value, op1, 0)
			} else if(op2 is MemNode) {
				invalidEncoding()
			} else {
				val width = op1.width ?: invalidEncoding()
				val imm = resolveImm(op2)

				if(!hasImmReloc && imm.isImm8 && width != BYTE) {
					encode1M(0x83, Widths.NO8, extension, op1, 1)
					writeImm(op2, BYTE, imm)
				} else {
					encode1M(0x80, Widths.ALL, extension, op1, width.bytes)
					writeImm(op2, width, imm)
				}
			}
		} else {
			invalidEncoding()
		}
	}



	/*
	ROL/ROR/RCL/RCR/SHL/SHR/SAR encodings
	 */



	private fun encodeROL(extension: Int, node: InsNode) {
		if(node.size != 2) invalidEncoding()
		val op1 = node.op1!!
		val op2 = node.op2!!

		if(op1 is RegNode) {
			if(op2 is RegNode) {
				if(op2.value != Register.CL) invalidEncoding()
				encode1R(0xD2, Widths.ALL, extension, op2.value)
			} else {
				val imm = resolveImm(op2)

				if(!hasImmReloc && imm == 1L) {
					encode1R(0xD0, Widths.ALL, extension, op1.value)
				} else {
					encode1R(0xC0, Widths.ALL, extension, op1.value)
					writeImm(op2, BYTE, imm)
				}
			}
		} else if(op1 is MemNode) {
			if(op2 is RegNode) {
				if(op2.value != Register.CL) invalidEncoding()
				encode1M(0xD2, Widths.ALL, extension, op1, 0)
			} else {
				val imm = resolveImm(op2)

				if(!hasImmReloc && imm == 1L) {
					encode1M(0xD0, Widths.ALL, extension, op1, 0)
				} else {
					encode1M(0xC0, Widths.ALL, extension, op1, 1)
					writeImm(op2, BYTE, imm)
				}
			}
		} else {
			invalidEncoding()
		}
	}




	/*
	MOV encodings
	 */



	/**
	 *     63  MOVSXD  R64_RM32   0111
	 */
	private fun encodeMOVSXD(op1: Register, op2: OpNode) {
		if(op1.width != QWORD)
			invalidEncoding()

		when(op2) {
			is RegNode -> {
				if(op2.width != DWORD) invalidEncoding()
				encode2RR(0x63, Widths.NO8, op1, op2.value, reversed = true, mismatch = true)
			}
			is MemNode -> {
				if(op2.width != DWORD) invalidEncoding()
				encode2RM(0x63, Widths.NO8, op1, op2, 0, true)
			}
			else -> invalidEncoding()
		}

	}



	/**
	 *     BE0F  MOVSX  R_RM8   0111
	 *     BF0F  MOVSX  R_RM16  0011
	 *     B60F  MOVZX  R_RM8   0111
	 *     B70F  MOVZX  R_RM16  0011
	 */
	private fun encodeMOVSX(opcode1: Int, opcode2: Int, op1: Register, op2: OpNode) {
		if(op2 is RegNode) {
			when(op2.value.width) {
				BYTE  -> encode2RR(opcode1, Widths.NO8, op1, op2.value, reversed = true, mismatch = true)
				WORD  -> encode2RR(opcode2, Widths.NO816, op1, op2.value, reversed = true, mismatch = true)
				else  -> invalidEncoding()
			}
		} else if(op2 is MemNode) {
			when(op2.width ?: invalidEncoding()) {
				BYTE  -> encode2RM(opcode1, Widths.NO8, op1, op2, 0, true)
				WORD  -> encode2RM(opcode2, Widths.NO816, op1, op2, 0, true)
				else  -> invalidEncoding()
			}
		}
	}



	/**
	 *     88  MOV  RM_R  1111
	 *     8A  MOV  R_RM  1111
	 *     B0  MOV  O_I   1111
	 *     C6  MOV  RM_I  1111
	 */
	private fun encodeMOV(op1: OpNode, op2: OpNode) {
		when(op1) {
			is RegNode -> when(op2) {
				is RegNode -> encode2RR(0x88, Widths.ALL, op1.value, op2.value, reversed = false)
				is MemNode -> encode2RM(0x8A, Widths.ALL, op1.value, op2, 0)
				else -> {
					val imm = resolveImm(op2)
					encode1O(0xB0, Widths.ALL, op1.value)
					writeImm(op2, op1.width, imm, true)
				}
			}
			is MemNode -> when(op2) {
				is RegNode -> encode2RM(0x88, Widths.ALL, op2.value, op1, 0)
				is MemNode -> invalidEncoding()
				else -> {
					val width = op1.width ?: invalidEncoding()
					encode1M(0xC6, Widths.ALL, 0, op1, width.bytes)
					writeImm(op2, width)
				}
			}
			else -> invalidEncoding()
		}
	}



	/*
	Pseudo-mnemonics
	 */



	private fun encodeDLLCALL(node: InsNode) {
		val op1 = node.op1 as? NameNode ?: invalidEncoding()
		op1.symbol = context.getDllImport(op1.name)
		if(op1.symbol == null) error("Unrecognised dll import: ${op1.name}")
		encode1M(0xFF, Widths.ONLY64, 2, MemNode(QWORD, op1), 0)
	}



	private fun encodeRETURN() {
		if(epilogueWriter.isEmpty)
			writer.i8(0xC3)
		else
			writer.bytes(epilogueWriter)
	}



	/*
	FPU
	 */



	private fun encodeFLD(node: InsNode) {
		when(val op1 = node.op1!!) {
			is MemNode -> when(op1.width) {
				DWORD -> encode1MAnyWidth(0xD9, 0, op1)
				QWORD -> encode1MAnyWidth(0xDD, 0, op1)
				TWORD -> encode1MAnyWidth(0xDB, 5, op1)
				else  -> invalidEncoding()
			}
			is FpuNode -> word(0xC0D9 + (op1.value.value shl 8))
			else -> invalidEncoding()
		}
	}



	/**
	 * FADD/FMUL/FCOM/FCOMP/FSUB/FSUBR/FDIV/FDIVR with 1 operand
	 */
	private fun encodeFADD1(node: InsNode, opcode: Int, extension: Int) {
		when(val op1 = node.op1!!) {
			is MemNode -> when(op1.width) {
				DWORD -> encode1MAnyWidth(0xD8, extension, op1)
				QWORD -> encode1MAnyWidth(0xDC, extension, op1)
				else  -> invalidEncoding()
			}
			is FpuNode -> word(opcode + (op1.value.value shl 8))
			else -> invalidEncoding()
		}
	}



	/**
	 * - [opcode1]: ST0, STI
	 * - [opcode2]: STI, ST0
	 */
	private fun encode2STST(node: InsNode, opcode1: Int, opcode2: Int) {
		val op1 = (node.op1 as? FpuNode)?.value ?: invalidEncoding()
		val op2 = (node.op2 as? FpuNode)?.value ?: invalidEncoding()

		if(op1 == FpuReg.ST0)
			word(opcode1 + (op2.value shl 8))
		else if(op2 == FpuReg.ST0)
			word(opcode2 + (op1.value shl 8))
		else
			invalidEncoding()
	}



	/**
	 * FIADD/FIMUL/FICOM/FICOMP/FISUB/FISUBR/FIDIV/FIDIVR
	 */
	private fun encodeFIADD(node: InsNode, extension: Int) {
		val op1 = node.op1 as? MemNode ?: invalidEncoding()
		when(op1.width) {
			WORD  -> encode1MAnyWidth(0xDE, extension, op1)
			DWORD -> encode1MAnyWidth(0xDA, extension, op1)
			else  -> invalidEncoding()
		}
	}



	private fun encode2ST0STI(node: InsNode, opcode: Int) {
		if(node.op1 !is FpuNode || node.op2 !is FpuNode) invalidEncoding()
		if(node.op1.value != FpuReg.ST0) invalidEncoding()
		word(opcode + (node.op2.value.value shl 8))
	}



	private fun encode2STIST0(node: InsNode, opcode: Int) {
		if(node.op1 !is FpuNode || node.op2 !is FpuNode) invalidEncoding()
		if(node.op2.value != FpuReg.ST0) invalidEncoding()
		word(opcode + (node.op1.value.value shl 8))
	}



	private fun encode1ST(opcode: Int, op1: FpuReg) {
		word(opcode + (op1.value shl 8))
	}



	/**
	 *     DD9B/7  FSTSW   M2BYTE
	 *     E0DF9B  FSTSW   AX
	 *     DD/7    FNSTSW  M2BYTE
	 *     E0DF    FNSTSW  AX
	 */
	private fun encodeFSTSW(op1: AstNode) {
		when(op1) {
			is RegNode ->
				if(op1.value == Register.AX)
					writer.i24(0xE0DF9B)
				else
					invalidEncoding()
			is MemNode -> encode1MNoWidth(0xDD9B, 7, op1)
			else -> invalidEncoding()
		}
	}




	/**
	 *     DD9B/7  FSTSW   M2BYTE
	 *     E0DF9B  FSTSW   AX
	 *     DD/7    FNSTSW  M2BYTE
	 *     E0DF    FNSTSW  AX
	 */
	private fun encodeFNSTSW(op1: AstNode) {
		when(op1) {
			is RegNode ->
				if(op1.value == Register.AX)
					writer.i16(0xE0DF)
				else
					invalidEncoding()
			is MemNode -> encode1MNoWidth(0xDD, 7, op1)
			else -> invalidEncoding()
		}
	}



	private fun encodeFCMOVCC(opcode: Int, node: InsNode) {
		val op1 = node.op1.asFpu
		val op2 = node.op2.asFpu
		if(op1 != FpuReg.ST0) invalidEncoding()
		writer.i16(opcode + (op2.value shl 8))
	}



	/**
	 *     D9/2  FST  M32FP
	 *     DD/2  FST  M64FP
	 *     D0DD  FST  STI
	 */
	private fun encodeFST(node: InsNode) {
		when(val op1 = node.op1) {
			is FpuNode -> word(0xD0DD + (op1.value.value shl 8))
			is MemNode -> when(op1.width) {
				DWORD  -> encode1MAnyWidth(0xD9, 2, op1)
				QWORD  -> encode1MAnyWidth(0xDD, 2, op1)
				else   -> invalidEncoding()
			}
			else -> invalidEncoding()
		}
	}



	/**
	 *     D9/3  FSTP  M32FP
	 *     DD/3  FSTP  M64FP
	 *     DB/7  FSTP  M80FP
	 *     D8DD  FSTP  STI
	 */
	private fun encodeFSTP(node: InsNode) {
		when(val op1 = node.op1) {
			is FpuNode -> word(0xD8DD + (op1.value.value shl 8))
			is MemNode -> when(op1.width) {
				DWORD  -> encode1MAnyWidth(0xD9, 3, op1)
				QWORD  -> encode1MAnyWidth(0xDD, 3, op1)
				TWORD  -> encode1MAnyWidth(0xDB, 7, op1)
				else   -> invalidEncoding()
			}
			else -> invalidEncoding()
		}
	}



	/**
	 *     DF/0  FILD  M16INT
	 *     DB/0  FILD  M32INT
	 *     DF/5  FILD  M64INT
	 */
	private fun encodeFILD(node: InsNode) {
		val op1 = node.op1 as? MemNode ?: invalidEncoding()
		when(op1.width) {
			WORD  -> encode1MAnyWidth(0xDF, 0, op1)
			DWORD -> encode1MAnyWidth(0xDB, 0, op1)
			QWORD -> encode1MAnyWidth(0xDF, 5, op1)
			else  -> invalidEncoding()
		}
	}



	/**
	 *     DF/1  FISTTP  M16INT
	 *     DB/1  FISTTP  M32INT
	 *     DD/1  FISTTP  M64INT
	 */
	private fun encodeFISTTP(node: InsNode) {
		val op1 = node.op1 as? MemNode ?: invalidEncoding()
		when(op1.width) {
			WORD  -> encode1MAnyWidth(0xDF, 1, op1)
			DWORD -> encode1MAnyWidth(0xDB, 1, op1)
			QWORD -> encode1MAnyWidth(0xDD, 1, op1)
			else  -> invalidEncoding()
		}
	}



	/**
	 *     DF/2  FIST  M16INT
	 *     DB/2  FIST  M32INT
	 */
	private fun encodeFIST(node: InsNode) {
		val op1 = node.op1 as? MemNode ?: invalidEncoding()
		when(op1.width) {
			WORD  -> encode1MAnyWidth(0xDF, 2, op1)
			DWORD -> encode1MAnyWidth(0xDB, 2, op1)
			else  -> invalidEncoding()
		}
	}



	/**
	 *     DF/3  FISTP  M16INT
	 *     DB/3  FISTP  M32INT
	 *     DF/7  FISTP  M64INT
	 */
	private fun encodeFISTP(node: InsNode) {
		val op1 = node.op1 as? MemNode ?: invalidEncoding()
		when(op1.width) {
			WORD  -> encode1MAnyWidth(0xDF, 3, op1)
			DWORD -> encode1MAnyWidth(0xDB, 3, op1)
			QWORD -> encode1MAnyWidth(0xDF, 7, op1)
			else  -> invalidEncoding()
		}
	}



	/*
	Misc.
	 */



	/**
	 *     020F  LAR  R16/32/64, R16/32/M16 ?
	 *     030F  LSL  R16/32/64, R16/32/M16 ?
	 */
	private fun encodeLAR(op1: OpNode, op2: OpNode, opcode: Int) {
		if(op1 !is RegNode || op1.width == BYTE) invalidEncoding()
		when(op2) {
			is MemNode -> {
				if(op2.width != null && op2.width != WORD) invalidEncoding()
				encode2RM(opcode, Widths.NO8, op1.value, op2, 0, true)
			}
			is RegNode -> {
				if(op2.width != WORD && op2.width != DWORD) invalidEncoding()
				encode2RR(opcode, Widths.NO8, op1.value, op2.value, reversed = true, mismatch = true)
			}
			else -> invalidEncoding()
		}
	}


}