package eyre

import eyre.Width.*
import eyre.encoding.EncodingReader

class Assembler(private val context: CompilerContext) {


	private val textWriter = context.textWriter

	private var writer = textWriter

	private var section = Section.TEXT



	fun assemble() {
		for(srcFile in context.srcFiles) {
			for(node in srcFile.nodes) {
				when(node) {
					is InsNode       -> handleInstruction(node)
					is LabelNode     -> handleLabel(node.symbol)
					is ProcNode      -> handleProc(node)
					is ScopeEndNode  -> handleScopeEnd(node)
					else             -> { }
				}
			}
		}
	}



	private fun invalidEncoding(): Nothing = error("Invalid encoding")



	/*
	Node assembly
	 */



	private val groups = EncodingReader.create("encodings.txt").let { it.read(); it.groups }

	private lateinit var group: EncodingGroup

	private lateinit var encoding: Encoding



	private fun encoding(ops: Ops) {
		encoding = if(ops !in group)
			invalidEncoding()
		else
			group[ops]
	}



	private fun handleInstruction(node: InsNode) {
		node.prefix?.let { byte(it.value) }

		group = groups[node.mnemonic] ?: invalidEncoding()

		customEncodings[node.mnemonic]?.let {
			it(node)
			return
		}

		when(node.size) {
			0 -> assemble0(node)
			1 -> assemble1(node, node.op1!!)
			2 -> assemble2(node, node.op1!!, node.op2!!)
			3 -> invalidEncoding()
			4 -> invalidEncoding()
		}
	}



	private fun handleProc(node: ProcNode) {
		handleLabel(node.symbol)
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
		if(node.symbol is ProcSymbol)
			node.symbol.size = writer.pos - node.symbol.pos
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
		is IntNode     -> node.value
		is UnaryNode   -> node.calculate(::resolveImmRec, regValid)
		is BinaryNode  -> node.calculate(::resolveImmRec, regValid)
		//is StringNode      -> node.value.ascii64()
		is SymNode     -> resolveImmSym(node.symbol, regValid)
		else           -> error("Invalid imm node: $node")
	}



	private fun resolveImm(node: ImmNode): Long {
		immRelocCount = 0
		return resolveImmRec(node.value, true)
	}



	private fun writeImm(node: ImmNode, width: Width, hasImm64: Boolean = false) {
		writeImm(node, width, resolveImm(node), hasImm64)
	}



	private fun writeImm(
		node     : ImmNode,
		width    : Width,
		value    : Long,
		hasImm64 : Boolean = false,
		isRel    : Boolean = false
	) {
		val actualWidth = if(width == QWORD && !hasImm64) DWORD else width
		if(node.width != null && node.width != actualWidth) invalidEncoding()

		if(isRel) {
			if(hasImmReloc)
				addRelReloc(actualWidth, node.value, 0)
			writer.writeWidth(actualWidth, value)
		} else if(immRelocCount == 1) {
			if(!hasImm64 || width != QWORD)
				error("Absolute relocations are only allowed with 64-bit operands")
			addAbsReloc(node.value)
			writer.advance(8)
		} else if(hasImmReloc) {
			addLinkReloc(actualWidth, node.value)
			writer.advance(actualWidth.bytes)
		} else if(!writer.writeWidth(actualWidth, value)) {
			invalidEncoding()
		}
	}



	/*
	Memory operands
	 */



	private var baseReg: Reg? = null
	private var indexReg: Reg? = null
	private var indexScale = 0
	private var aso = Aso.NONE
	private var memRelocCount = 0
	private val hasMemReloc get() = memRelocCount > 0
	private val memRexX get() = indexReg?.rex ?: 0
	private val memRexB get() = baseReg?.rex ?: 0

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



	private fun resolveMemReg(reg: Reg, regValid: Boolean): Long {
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
		if(baseReg != null && indexReg != null && indexReg!!.value == 4) {
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

	private fun byte(value: Int) = writer.i8(value)

	private fun word(value: Int) = writer.i16(value)

	private fun dword(value: Int) = writer.i32(value)

	private fun checkMask(width: Width) {
		if(width !in encoding.mask) invalidEncoding()
	}

	private fun checkO16(width: Width) {
		if(encoding.o16 || (width == WORD && encoding.mask != OpMask.WORD))
			writer.i8(0x66)
	}

	private fun checkPrefix() {
		if(encoding.prefix != 0)
			writer.i8(encoding.prefix)
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

	private fun writeVEX1(r: Int, x: Int, b: Int, m: Int) {
		writer.i8((r shl 7) or (x shl 6) or (b shl 5) or m)
	}

	private fun writeVEX2(w: Int, v: Int, l: Int, pp: Int) {
		writer.i8((w shl 7) or (v shl 3) or (l shl 2) or pp)
	}

	private fun writeEVEX1(r: Int, x: Int, b: Int, r2: Int, mm: Int) {
		writer.i8((r shl 7) or (x shl 6) or (b shl 5) or (r2 shl 4) or mm)
	}

	private fun writeEVEX2(w: Int, vvvv: Int, pp: Int) {
		writer.i8((w shl 7) or (vvvv shl 3) or (1 shl 2) or pp)
	}

	private fun writeEVEX3(z: Int, l2: Int, l: Int, b: Int, v2: Int, aaa: Int) {
		writer.i8((z shl 7) or (l2 shl 6) or (l shl 5) or (b shl 4) or (v2 shl 3) or aaa)
	}

	/** Return 1 if width is QWORD and widths has DWORD set, otherwise 0 */
	private fun rexw(width: Width) =
		((width.bytes shr 3) and (encoding.mask.value shr 2)) or encoding.rexw

	private fun writeOpcode(width: Width = DWORD, addition: Int = 0) {
		when(encoding.escape) {
			1 -> writer.i8(0x0F)
			2 -> writer.i16(0x380F)
			3 -> writer.i16(0x3A0F)
			4 -> writer.i16(0x000F)
		}

		/** Add one if width is not BYTE and if widths has BYTE set */
		val addition2 = addition + ((encoding.mask.value and 1) and (1 shl width.ordinal).inv())
		/** Add to the encoding opcode's MSB */
		val finalOpcode = encoding.opcode or (addition2 shl ((encoding.oplen - 1) shl 3))
		writer.i16(writer.pos, finalOpcode)
		writer.pos += encoding.oplen
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



	private fun writeMem(node: AstNode, reg: Int, disp: Long, immLength: Int) {
		val base  = baseReg
		val index = indexReg
		val scale = indexScale.countTrailingZeroBits()

		if(aso == Aso.R32) writer.i8(0x67)

		val mod = when {
			hasMemReloc -> 2 // disp32
			disp == 0L -> if(base != null && base.value == 5)
				1 // disp8, rbp as base needs an empty offset
			else
				0 // no disp
			disp.isImm8 -> 1 // disp8
			else -> 2 // disp32
		}

		if(index != null) { // SIB
			if(base != null) {
				writeModRM(mod, reg, 0b100)
				writeSib(scale, index.value, base.value)
				relocAndDisp(mod, disp, node)
			} else {
				writeModRM(mod, reg, 0b100)
				writeSib(scale, index.value, 0b101)
				relocAndDisp(mod, disp, node)
			}
		} else if(base != null) { // Indirect
			if(base.value == 5) {
				writeModRM(mod, reg, 0b100)
				writeSib(0, 0b100, 0b100)
			} else {
				writeModRM(mod, reg, base.value)
			}
			relocAndDisp(mod, disp, node)
		} else if(memRelocCount and 1 == 1) { // RIP-relative
			writeModRM(0b00, reg, 0b101)
			addRelReloc(DWORD, node, immLength)
			dword(0)
		} else if(mod != 0) { // Absolute 32-bit (Not working?)
			writeModRM(0b00, reg, 0b100)
			writeSib(0b00, 0b100, 0b101)
			relocAndDisp(mod, disp, node)
		} else {
			invalidEncoding() // Empty memory operand
		}
	}



	// Basic encoding



	private fun encodeNone() {
		if(encoding.o16) writer.i8(0x66)
		checkPrefix()
		if(encoding.rexw == 1) writer.i8(0x48)
		writeOpcode(DWORD)
	}



	private fun encode1R(op1: Reg) {
		val width = op1.width
		checkMask(width)
		checkO16(width)
		checkPrefix()
		writeRex(rexw(width), 0, 0, op1.rex)
		writeOpcode(width)
		writeModRM(0b11, encoding.extension, op1.value)
	}



	private fun encode1O(opcode: Int, oplen: Int, op1: Reg, mask: OpMask) {
		val width = op1.width
		if(width !in mask) invalidEncoding()
		if(width == WORD && encoding.mask != OpMask.WORD) writer.i8(0x66)
		val rexw = ((width.bytes shr 3) and (mask.value shr 2))
		writeRex(rexw, 0, 0, op1.rex)
		writer.varLengthInt(opcode + (op1.value shl ((oplen - 1) shl 3)))
	}



	private fun encode1O(op1: Reg) {
		val width = op1.width
		checkMask(width)
		checkO16(width)
		checkPrefix()
		writeRex(rexw(width), 0, 0, op1.rex)
		writeOpcode(width, op1.value)
	}



	private fun encode1ST(op1: Reg) {
		checkPrefix()
		writeOpcode(addition = op1.value)
	}



	private fun encode1RA(op1: Reg) {
		val width = op1.width
		if(width == DWORD) writer.i8(0x67)
		checkMask(width)
		checkO16(width)
		checkPrefix()
		writeRex(0, 0, 0, op1.rex)
		writeOpcode(width)
		writeModRM(0b11, encoding.extension, op1.value)
	}



	private fun encode1M(op1: MemNode) {
		if(encoding.mask.isNotEmpty) checkMask(op1.width ?: invalidEncoding())
		else if(op1.width != null) invalidEncoding()
		val width = op1.width ?: DWORD
		checkO16(width)
		val disp = resolveMem(op1.value)
		writeRex(rexw(width), 0, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
		writeOpcode(width)
		writeMem(op1, encoding.extension, disp, 0)
	}



	private fun encode2RR(op1: Reg, op2: Reg) {
		var width = op1.width

		if(encoding.mismatch) {
			if(width !in encoding.mask) invalidEncoding()
			if(op2.width !in encoding.mask2) invalidEncoding()
			if(encoding.widthOp == 1) width = op2.width
		} else {
			if(op2.width != width) invalidEncoding()
			checkMask(width)
		}

		checkO16(width)
		writeRex(rexw(width), op1.rex, 0, op2.rex)
		writeOpcode(width)
		writeModRM(0b11, op1.value, op2.value)
	}



	/*private fun encode1M(opcode: Int, mask: OpMask, node: MemNode) {
		val width = node.width ?: invalidEncoding()
		checkWidths(node.width)
		checkO16(width)
		val disp = resolveMem(node.value)
		writeRex(rexw(width), 0, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
		writeOpcode(getOpcode(opcode, mask, width))
		writeMem(node, 0, disp, 0)
	}



	private fun encode1MAnyWidth(opcode: Int, node: MemNode) {
		val disp = resolveMem(node.value)
		writeRex(0, 0, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
		writeOpcode(opcode)
		writeMem(node, 0, disp, 0)
	}



	private fun encode1MSingleWidth(opcode: Int, node: MemNode, width: Width) {
		if(node.width != null && node.width != width) invalidEncoding()
		encode1MAnyWidth(opcode, node)
	}



	private fun encode1MNoWidth(opcode: Int, node: MemNode) {
		if(node.width != null) invalidEncoding()
		encode1MAnyWidth(opcode, node)
	}


	private fun encode1R(opcode: Int, opMask: OpMask, extension: Int, op1: Reg) {
		val width = op1.width
		checkWidths(opMask, width)
		checkO16(width)
		writeRex(rexw(opMask, width), 0, 0, op1.rex)
		writeOpcode(opcode, opMask, width)
		writeModRM(0b11, extension, op1.value)
	}



	private fun encode2RM(
		opcode: Int,
		mask: OpMask,
		op1: Reg,
		op2: MemNode,
		immLength: Int,
		mismatch: Boolean = false
	) {
		val width = op1.width
		if(!mismatch && op2.width != null && op2.width != op1.width) invalidEncoding()
		checkWidths(mask, width)
		checkO16(width)
		val disp = resolveMem(op2.value)
		writeRex(rexw(mask, width), op1.rex, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
		writeOpcode(getOpcode(opcode, mask, width))
		writeMem(op2, 0, disp, immLength)
	}*/



	/*
	Assembly
	 */



	private val customEncodings = mapOf(
		Mnemonic.MOV   to ::encodeMOV,
	)



	private fun assemble0(node: InsNode) {
		encoding(Ops.NONE)
		encodeNone()
	}




	private fun encodeMOV(node: InsNode) {

	}



	private fun assemble1(node: InsNode, op1: OpNode) {
		if(op1 is RegNode) {
			val r1 = op1.value

			if(r1.isR) {
				if(Ops.R in group) {
					encoding = group[Ops.R]
					encode1R(r1)
				} else if(Ops.O in group) {
					encoding = group[Ops.O]
					encode1O(r1)
				} else if(Ops.RA in group) {
					encoding = group[Ops.RA]
					encode1RA(r1)
				} else if(Ops.AX in group) {
					if(r1 != Reg.AX) invalidEncoding()
					encoding = group[Ops.AX]
					encodeNone()
				} else {
					invalidEncoding()
				}
			} else if(r1.type == RegType.ST) {
				encoding(Ops.ST)
				encode1ST(r1)
			} else if(r1 == Reg.FS) {
				encoding(Ops.FS)
				encodeNone()
			} else if(r1 == Reg.GS) {
				encoding(Ops.GS)
				encodeNone()
			} else {
				invalidEncoding()
			}
		} else if(op1 is MemNode) {
			encoding(Ops.M)
			encode1M(op1)
		} else if(op1 is ImmNode) {
			val imm = resolveImm(op1)

			fun imm(ops: Ops, width: Width, rel: Boolean) {
				encoding(ops)
				encodeNone()
				writeImm(op1, width, imm, false, rel)
			}

			when {
				node.mnemonic == Mnemonic.PUSH -> when {
					op1.width == BYTE  -> imm(Ops.I8, BYTE, false)
					op1.width == WORD  -> imm(Ops.I16, WORD, false)
					op1.width == DWORD -> imm(Ops.I32, DWORD, false)
					hasImmReloc -> imm(Ops.I32, DWORD, false)
					imm.isImm8  -> imm(Ops.I8, BYTE, false)
					imm.isImm16 -> imm(Ops.I16, WORD, false)
					else        -> imm(Ops.I32, DWORD, false)
				}
				Ops.REL32 in group ->
					if(Ops.REL8 in group && ((!hasImmReloc && imm.isImm8) || op1.width == BYTE))
						imm(Ops.REL8, BYTE, true)
					else
						imm(Ops.REL32, DWORD, true)
				Ops.REL8 in group -> imm(Ops.REL8, BYTE, true)
				Ops.I8   in group -> imm(Ops.I8, BYTE, false)
				Ops.I16  in group -> imm(Ops.I16, WORD, false)
				else              -> invalidEncoding()
			}
		}
	}



	private fun assemble2(node: InsNode, op1: OpNode, op2: OpNode) {
		if(op1 is RegNode) {
			val r1 = op1.value

			if(op2 is RegNode) {
				val r2 = op2.value

				if(r2.isR) {
					if(Ops.A_O in group) {
						if(r1.isA) {
							encoding = group[Ops.A_O]
							encode1O(r2)
						} else if(r2.isA) {
							encoding = group[Ops.A_O]
							encode1O(r1)
						} else {
							encoding(Ops.R_R)
							encode2RR(r1, r2)
						}
					} else if(Ops.RM_CL in group && r2 == Reg.CL) {
						encoding = group[Ops.RM_CL]
						encode1R(r1)
					} else if(Ops.R_R in group) {
						encoding = group[Ops.R_R]
						encode2RR(r1, r2)
					} else {
						invalidEncoding()
					}
				} else if(r1 == Reg.ST0) {
					encoding(Ops.ST0_ST)
					encode1ST(r2)
				} else if(r2 == Reg.ST0) {
					encoding(Ops.ST_ST0)
					encode1ST(r1)
				} else {
					invalidEncoding()
				}
			} else if(op2 is MemNode) {

			} else if(op2 is ImmNode) {

			}
		} else if(op1 is MemNode) {
			if(op2 is RegNode) {

			} else if(op2 is ImmNode) {

			} else {
				invalidEncoding()
			}
		} else {
			invalidEncoding()
		}
	}



}