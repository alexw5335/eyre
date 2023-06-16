package eyre

import eyre.Width.*
import eyre.gen.ManualParser

class Assembler(private val context: CompilerContext) {


	private val textWriter = context.textWriter

	private var writer = textWriter

	private var section = Section.TEXT

	private val groups = ManualParser("encodings.txt").let { it.read(); it.groups }

	private lateinit var group: EncodingGroup

	private lateinit var encoding: Encoding



	fun assemble() {
		for(srcFile in context.srcFiles) {
			for(node in srcFile.nodes) {
				when(node) {
					is InsNode       -> assemble(node)
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



	private fun byte(value: Int) = writer.i8(value)

	private fun word(value: Int) = writer.i16(value)

	private fun dword(value: Int) = writer.i32(value)

	/** [w]: 64-bit override [r]: REG, [x]: INDEX, [b]: RM, BASE, or OPREG */
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

	private fun writeSseOpcode() {
		writeEscape()
		writer.i8(encoding.opcode)
	}


	private fun writeEscape() {
		when(encoding.escape) {
			Escape.NONE -> Unit
			Escape.E0F  -> writer.i8(0x0F)
			Escape.E38  -> writer.i16(0x380F)
			Escape.E3A  -> writer.i16(0x3A0F)
			Escape.E00  -> writer.i16(0x000F)
		}
	}
	
	private fun writeOpcode(width: Width = DWORD, addition: Int = 0) {
		writeEscape()
		val oplen = if(encoding.opcode and 0xFF00 != 0) 2 else 1
		/** Add one if width is not BYTE and if widths has BYTE set */
		val addition2 = addition + ((encoding.mask.value and 1) and (1 shl width.ordinal).inv())
		/** Add to the encoding opcode's MSB */
		val finalOpcode = encoding.opcode or (addition2 shl ((oplen - 1) shl 3))
		writer.i16(writer.pos, finalOpcode)
		writer.pos += oplen
	}

	private fun checkMask(width: Width) {
		if(width !in encoding.mask) invalidEncoding()
	}

	private fun checkO16(width: Width) {
		if(encoding.o16 == 1 || (width == WORD && encoding.mask != OpMask.WORD))
			writer.i8(0x66)
	}

	private fun checkPrefix() {
		when(encoding.prefix) {
			Prefix.NONE -> Unit
			Prefix.P66 -> writer.i8(0x66)
			Prefix.PF2 -> writer.i8(0xF2)
			Prefix.PF3 -> writer.i8(0xF3)
			Prefix.P9B -> writer.i8(0x9B)
			Prefix.P67 -> writer.i8(0x67)
		}
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

		//if(aso == Aso.R32) writer.i8(0x67)

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



	/*
	Encoding
	 */



	private fun encodeNone() {
		if(encoding.o16 == 1) writer.i8(0x66)
		checkPrefix()
		if(encoding.rexw == 1) writer.i8(0x48)
		writeOpcode(DWORD)
	}



	private fun encodeNone(width: Width) {
		checkO16(width)
		checkPrefix()
		writeRex(rexw(width), 0, 0, 0)
		writeOpcode(width)
	}



	private fun encode1R(op1: Reg) {
		val width = op1.width
		val a32 = Ops.RA in group
		checkMask(width)
		if(a32 && op1.width == DWORD) byte(0x67)
		checkO16(width)
		checkPrefix()
		writeRex(if(a32) 0 else rexw(width), 0, 0, op1.rex)
		writeOpcode(width)
		writeModRM(0b11, encoding.extension, op1.value)
	}


	private fun encode1O(op1: Reg) {
		val width = op1.width
		checkMask(width)
		checkO16(width)
		checkPrefix()
		writeRex(rexw(width), 0, 0, op1.rex)
		writeOpcode(width, op1.value + if(op1.type != RegType.R8) 7 else 0)
	}



	private fun encode1ST(op1: Reg) {
		checkPrefix()
		writeOpcode(addition = op1.value)
	}



	private fun encode1M(op1: MemNode) {
		if(encoding.mask.isNotEmpty) checkMask(op1.width ?: invalidEncoding())
		else if(op1.width != null) invalidEncoding()
		val width = op1.width ?: DWORD
		checkO16(width)
		checkPrefix()
		val disp = resolveMem(op1.value)
		writeRex(rexw(width), 0, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
		writeOpcode(width)
		writeMem(op1, encoding.extension, disp, 0)
	}



	private fun encode2RR(op1: Reg, op2: Reg) {
		val width = op1.width
		checkMask(width)
		checkO16(width)
		checkPrefix()
		if(Ops.M_R !in group && Ops.RM_R_CL !in group) {
			writeRex(rexw(width), op1.rex, 0, op2.rex)
			writeOpcode(width)
			writeModRM(0b11, op1.value, op2.value)
		} else {
			writeRex(rexw(width), op2.rex, 0, op1.rex)
			writeOpcode(width)
			writeModRM(0b11, op2.value, op1.value)
		}
	}



	private fun encode2RM(op1: Reg, op2: MemNode) {
		val width = op1.width
		val a32 = Ops.RA_M512 in group
		checkMask(op1.width)
		if(a32 && op1.width == DWORD) byte(0x67)
		checkO16(width)
		checkPrefix()
		val disp = resolveMem(op2.value)
		writeRex(if(a32) 0 else rexw(width), op1.rex, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
		writeOpcode(width)
		writeMem(op2, op1.value, disp, 0)
	}



	private fun encodeMOVRR(op1: Reg, op2: Reg, opcode: Int) {
		if(group.mnemonic != Mnemonic.MOV) invalidEncoding()
		if(op2.type != RegType.R64) invalidEncoding()
		writeRex(0, op1.rex, 0, op2.rex)
		writer.varLengthInt(opcode)
		writeModRM(0b11, op1.value, op2.value)
	}



	private fun encodeMOVRSEG(op1: Reg, op2: Reg, r: Reg, opcode: Int) {
		if(group.mnemonic != Mnemonic.MOV) invalidEncoding()
		when(r.type) {
			RegType.R16 -> { byte(0x66); writeRex(0, op1.rex, 0, op2.rex) }
			RegType.R32 -> writeRex(0, op1.rex, 0, op2.rex)
			RegType.R64 -> writeRex(1, op1.rex, 0, op2.rex)
			else        -> invalidEncoding()
		}
		byte(opcode)
		writeModRM(0b11, op1.value, op2.value)
	}



	/*
	Assembly
	 */



	private fun encoding(ops: Ops) {
		encoding = if(ops !in group)
			invalidEncoding()
		else
			group[ops]
	}



	private fun encoding(ops: SseOps) {
		for(e in group.encodings) {
			if(e.sseOps == ops) {
				encoding = e
				return
			}
		}

		invalidEncoding()
	}



	private val Reg.sseOp get() = when(type) {
		RegType.R8  -> SseOp.R8
		RegType.R16 -> SseOp.R16
		RegType.R32 -> SseOp.R32
		RegType.R64 -> SseOp.R64
		RegType.X   -> SseOp.X
		RegType.MM  -> SseOp.MM
		else        -> SseOp.NONE
	}




	private fun encodeSseRR(op1: Reg, op2: Reg, op3: OpNode?) {
		if(op1.high != 0 || op2.high != 0) invalidEncoding()
		encoding(SseOps(op1.sseOp, op2.sseOp, if(op3 is ImmNode) SseOp.I8 else SseOp.NONE))

		if(encoding.o16 == 1) byte(0x66)
		if(encoding.prefix != Prefix.NONE) byte(encoding.prefix.value)

		if(!encoding.mr) {
			writeRex(encoding.rexw, op1.rex, 0, op2.rex)
			writeSseOpcode()
			writeModRM(0b11, op1.value, op2.value)
		} else {
			writeRex(encoding.rexw, op2.rex, 0, op1.rex)
			writeSseOpcode()
			writeModRM(0b11, op2.value, op1.value)
		}

		if(op3 is ImmNode)
			writeImm(op3, BYTE)
	}



	private fun encodeSseRM(op1: Reg, op2: MemNode, op3: OpNode?, reversed: Boolean) {
		if(op1.high != 0) invalidEncoding()

		if(group.mnemonic == Mnemonic.CVTSI2SD || group.mnemonic == Mnemonic.CVTSI2SS) {
			if(op1.type != RegType.X)
				invalidEncoding()
			encoding = when(op2.width) {
				null,
				DWORD -> group.encodings.first { it.mask == OpMask.DWORD }
				QWORD -> group.encodings.first { it.mask == OpMask.QWORD }
				else  -> invalidEncoding()
			}
		} else {
			if(!reversed)
				encoding(SseOps(op1.sseOp, SseOp.M, if(op3 is ImmNode) SseOp.I8 else SseOp.NONE))
			else
				encoding(SseOps(SseOp.M, op1.sseOp, if(op3 is ImmNode) SseOp.I8 else SseOp.NONE))

			if(op2.width != null && op2.width !in encoding.mask)
				invalidEncoding()
		}

		val disp = resolveMem(op2.value)
		if(aso == Aso.R32) byte(0x67)
		if(encoding.o16 == 1) byte(0x66)
		if(encoding.prefix != Prefix.NONE) byte(encoding.prefix.value)
		writeRex(encoding.rexw, op1.rex, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
		writeSseOpcode()
		writeMem(op2, op1.value, disp, 0)

		if(op3 is ImmNode)
			writeImm(op3, BYTE)
	}



	private fun encodeSseRI(op1: Reg, op2: ImmNode, op3: OpNode?) {
		if(op3 != null) invalidEncoding()
		if(encoding.o16 == 1) byte(0x66)
		if(encoding.prefix != Prefix.NONE) byte(encoding.prefix.value)
		writeRex(encoding.rexw, op1.rex, 0, 0)
		writeSseOpcode()
		writeModRM(0b11, encoding.extension, op1.value)
		writeImm(op2, BYTE)
	}



	private fun assembleSse(node: InsNode) {
		when(node.op1) {
			is RegNode -> when(node.op2) {
				is RegNode -> encodeSseRR(node.op1.value, node.op2.value, node.op3)
				is MemNode -> encodeSseRM(node.op1.value, node.op2, node.op3, false)
				is ImmNode -> encodeSseRI(node.op1.value, node.op2, node.op3)
				else       -> invalidEncoding()
			}
			is MemNode -> encodeSseRM((node.op2 as? RegNode)?.value ?: invalidEncoding(), node.op1, node.op3, true)
			else -> invalidEncoding()
		}
	}



	private fun assemble(node: InsNode) {
		node.prefix?.let { byte(it.value) }

		group = groups[node.mnemonic] ?: invalidEncoding()

		if(group.isSse)
			assembleSse(node)
		else
			assembleGp(node)
	}



	private fun assembleGp(node: InsNode) {
		when(node.size) {
			0 -> { encoding(Ops.NONE); encodeNone() }
			1 -> when(val op1 = node.op1!!) {
				is RegNode -> assemble1R(op1.value)
				is MemNode -> assemble1M(op1)
				is ImmNode -> assemble1I(op1)
			}
			2 -> when(val op1 = node.op1!!) {
				is RegNode -> when(val op2 = node.op2!!) {
					is RegNode -> assemble2RR(op1.value, op2.value)
					is MemNode -> assemble2RM(op1.value, op2)
					is ImmNode -> assemble2RI(op1.value, op2)
				}
				is MemNode -> when(val op2 = node.op2!!) {
					is RegNode -> assemble2MR(op1, op2.value)
					is MemNode -> invalidEncoding()
					is ImmNode -> assemble2MI(op1, op2)
				}
				is ImmNode -> when(val op2 = node.op2!!) {
					is RegNode -> assemble2IR(op1, op2.value)
					is ImmNode -> assemble2II(op1, op2)
					else -> invalidEncoding()
				}
			}
			3 -> assemble3(node.op1!!, node.op2!!, node.op3!!)
			4 -> invalidEncoding()
		}
	}



	/*
	1-operand assembly
	 */



	private fun assemble1R(op1: Reg) {
		when {
			op1.isR -> when {
				Ops.RA in group ||
				Ops.R in group -> { encoding = group[Ops.R]; encode1R(op1) }
				Ops.O in group -> { encoding = group[Ops.O]; encode1O(op1) }
				op1 == Reg.AX -> { encoding(Ops.AX); encodeNone() }
				else -> invalidEncoding()
			}
			op1.type == RegType.ST -> { encoding(Ops.ST); encode1ST(op1) }
			op1 == Reg.FS -> { encoding(Ops.FS); encodeNone() }
			op1 == Reg.GS -> { encoding(Ops.GS); encodeNone() }
			else -> invalidEncoding()
		}
	}



	private fun assemble1M(op1: MemNode) {
		encoding(Ops.M)
		encode1M(op1)
	}



	private fun assemble1I(op1: ImmNode) {
		val imm = resolveImm(op1)

		fun imm(ops: Ops, width: Width, rel: Boolean) {
			encoding(ops)
			encodeNone()
			writeImm(op1, width, imm, false, rel)
		}

		when {
			group.mnemonic == Mnemonic.PUSH -> when {
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



	/*
	2-operand assembly
	 */



	private fun assemble2MR(op1: MemNode, op2: Reg) {
		when {
			op1.width != null && op1.width != op2.width -> invalidEncoding()

			op2.type == RegType.SEG -> {
				encoding(Ops.M_SEG)
				encode2RM(op2, op1)
			}

			Ops.M_R in group -> {
				encoding = group[Ops.M_R]
				encode2RM(op2, op1)
			}

			else -> invalidEncoding()
		}
	}



	private fun assemble2MI(op1: MemNode, op2: ImmNode) {
		val imm = resolveImm(op2)

		fun encode(ops: Ops, width: Width) {
			encoding = group[ops]
			encode1M(op1)
			writeImm(op2, width, imm)
		}

		when {
			Ops.RM_I8 in group -> when {
				!hasImmReloc && imm.isImm8 -> encode(Ops.RM_I8, BYTE)
				Ops.RM_I in group -> encode(Ops.RM_I, op1.width ?: invalidEncoding())
				else -> invalidEncoding()
			}
			Ops.RM_I in group -> encode(Ops.RM_I, op1.width ?: invalidEncoding())
		}
	}



	private fun assemble2IR(op1: ImmNode, op2: Reg) {
		if(Ops.I8_A !in group) invalidEncoding()

		when(op2) {
			Reg.AL  -> { byte(0xE6); writeImm(op1, BYTE) }
			Reg.AX  -> { word(0xE766); writeImm(op1, BYTE) }
			Reg.EAX -> { byte(0xE7); writeImm(op1, BYTE) }
			else    -> invalidEncoding()
		}
	}



	private fun assemble2II(op1: ImmNode, op2: ImmNode) {
		encoding(Ops.I16_I8)
		val imm1 = resolveImm(op1)
		if(hasImmReloc || !imm1.isImm16) invalidEncoding()
		val imm2 = resolveImm(op2)
		if(hasImmReloc || !imm1.isImm8) invalidEncoding()
		encodeNone()
		word(imm1.toInt())
		byte(imm2.toInt())
	}



	private fun assemble2RI(op1: Reg, op2: ImmNode) {
		val imm = resolveImm(op2)

		fun encodeAI() {
			encoding = group[Ops.A_I]
			encodeNone(op1.width)
			writeImm(op2, op1.width, imm)
		}

		fun encode(ops: Ops, width: Width) {
			encoding = group[ops]
			encode1R(op1)
			writeImm(op2, width, imm)
		}

		when {
			Ops.O_I in group -> {
				encoding = group[Ops.O_I]
				encode1O(op1)
				writeImm(op2, op1.width, imm, true)
			}
			Ops.RM_I8 in group -> when {
				!hasImmReloc && imm.isImm8 -> encode(Ops.RM_I8, BYTE)
				Ops.A_I in group -> encodeAI()
				Ops.RM_I in group -> encode(Ops.RM_I, op1.width)
				else -> invalidEncoding()
			}
			Ops.A_I in group -> encodeAI()
			Ops.RM_I in group -> encode(Ops.RM_I, op1.width)
			Ops.A_I8 in group -> when(op1) {
				Reg.AL  -> { byte(0xE4); writeImm(op2, BYTE) }
				Reg.AX  -> { word(0xE566); writeImm(op2, BYTE) }
				Reg.EAX -> { byte(0xE5); writeImm(op2, BYTE) }
				else    -> invalidEncoding()
			}
			else -> invalidEncoding()
		}
	}



	private fun assemble2RM(op1: Reg, op2: MemNode) {
		when {
			Ops.R_MEM in group -> { encoding(Ops.R_MEM); encode2RM(op1, op2) }

			op2.width != null && op1.width != op2.width -> when(op2.width) {
				BYTE  -> { encoding(Ops.R_RM8); encode2RM(op1, op2) }
				WORD  -> { encoding(Ops.R_RM16); encode2RM(op1, op2) }
				DWORD -> { encoding(Ops.R_RM32); encode2RM(op1, op2) }
				XWORD -> { encoding(Ops.R_M128); encode2RM(op1, op2) }
				ZWORD -> { encoding(Ops.RA_M512); encode2RM(op1, op2) }
				else  -> invalidEncoding()
			}

			op1.type == RegType.SEG -> { encoding(Ops.SEG_M); encode2RM(op1, op2) }
			Ops.R_M in group -> { encoding = group[Ops.R_M]; encode2RM(op1, op2) }

			Ops.R_RM32 in group -> { encoding = group[Ops.R_RM32]; encode2RM(op1, op2) }
			Ops.R_M128 in group -> { encoding = group[Ops.R_M128]; encode2RM(op1, op2) }
			Ops.RA_M512 in group -> { encoding = group[Ops.RA_M512]; encode2RM(op1, op2) }

			else -> invalidEncoding()
		}
	}



	private fun assemble2RR(op1: Reg, op2: Reg) {
		when {
			op1.isR && op2.isR -> when {
				op1.width != op2.width -> when {
					// R8_CL?
					Ops.RM_CL in group -> when(op2) {
						Reg.CL -> { encoding = group[Ops.RM_CL]; encode1R(op1) }
						else   -> { encoding(Ops.R_R); encode2RR(op1, op2) }
					}

					Ops.A_DX in group -> when {
						!op1.isA ||
						op2 != Reg.DX  -> invalidEncoding()
						op1 == Reg.AL  -> byte(0xEC)
						op1 == Reg.AX  -> word(0xED66)
						op1 == Reg.EAX -> byte(0xED)
						else           -> invalidEncoding()
					}

					Ops.DX_A in group -> when {
						!op2.isA ||
							op1 != Reg.DX  -> invalidEncoding()
						op2 == Reg.AL  -> byte(0xEE)
						op2 == Reg.AX  -> word(0xEF66)
						op2 == Reg.EAX -> byte(0xEF)
						else           -> invalidEncoding()
					}

					else -> when(op2.width) {
						BYTE  -> { encoding(Ops.R_RM8); encode2RR(op1, op2) }
						WORD  -> { encoding(Ops.R_RM16); encode2RR(op1, op2) }
						DWORD -> { encoding(Ops.R_RM32); encode2RR(op1, op2) }
						else  -> invalidEncoding()
					}
				}

				Ops.A_O in group -> when {
					op1.isA -> { encoding = group[Ops.A_O]; encode1O(op2) }
					op2.isA -> { encoding = group[Ops.A_O]; encode1O(op1) }
					else    -> { encoding = group[Ops.R_R]; encode2RR(op1, op2) }
				}

				Ops.R_R in group -> {
					encoding = group[Ops.R_R]
					encode2RR(op1, op2)
				}

				else -> invalidEncoding()
			}

			// FPU
			op1 == Reg.ST0 -> { encoding(Ops.ST0_ST); encode1ST(op2) }
			op2 == Reg.ST0 -> { encoding(Ops.ST_ST0); encode1ST(op1) }

			// MOV
			op1.type == RegType.CR -> encodeMOVRR(op1, op2, 0x220F)
			op2.type == RegType.CR -> encodeMOVRR(op2, op1, 0x200F)
			op1.type == RegType.DR -> encodeMOVRR(op1, op2, 0x230F)
			op2.type == RegType.DR -> encodeMOVRR(op2, op1, 0x210F)
			op1.type == RegType.SEG -> encodeMOVRSEG(op1, op2, op2, 0x8E)
			op2.type == RegType.SEG -> encodeMOVRSEG(op2, op1, op1, 0x8C)

			else -> invalidEncoding()
		}
	}



	/*
	3-operand assembly
	 */



	private fun assemble3(op1: OpNode, op2: OpNode, op3: OpNode) {
		if(op3 is RegNode) {
			if(op3.value != Reg.CL || op2 !is RegNode) invalidEncoding()
			encoding(Ops.RM_R_CL)
			when(op1) {
				is RegNode -> {
					if(op1.width != op2.width) invalidEncoding()
					encode2RR(op1.value, op2.value)
				}
				is MemNode -> {
					if(op1.width != null && op1.width != op2.width) invalidEncoding()
					encode2RM(op2.value, op1)
				}
				else -> invalidEncoding()
			}
			return
		}

		val imm = resolveImm(op3 as? ImmNode ?: invalidEncoding())

		when(op1) {
			is RegNode -> when(op2) {
				is RegNode -> {
					if(op1.width != op2.width)
						invalidEncoding()

					if(Ops.R_R_I8 in group && imm.isImm8 && !hasImmReloc) {
						encoding(Ops.R_R_I8)
						encode2RR(op1.value, op2.value)
						writeImm(op3, BYTE, imm)
					} else {
						encoding(Ops.R_RM_I)
						encode2RR(op1.value, op2.value)
						writeImm(op3, op1.width, imm)
					}
				}

				is MemNode -> {
					if(op1.width != op2.width) invalidEncoding()

					if(Ops.R_M_I8 in group && imm.isImm8 && !hasImmReloc) {
						encoding(Ops.R_M_I8)
						encode2RM(op1.value, op2)
						writeImm(op3, BYTE, imm)
					} else {
						encoding(Ops.R_RM_I)
						encode2RM(op1.value, op2)
						writeImm(op3, op1.width, imm)
					}
				}

				else -> invalidEncoding()
			}

			is MemNode -> {
				if(op2 !is RegNode) invalidEncoding()
				if(op1.width != null && op1.width != op2.width) invalidEncoding()
				encoding(Ops.M_R_I8)
				encode2RM(op2.value, op1)
				writeImm(op3, BYTE, imm)
			}

			else -> invalidEncoding()
		}
	}


}