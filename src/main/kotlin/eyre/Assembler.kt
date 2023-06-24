package eyre

import eyre.util.NativeWriter
import eyre.Width.*
import eyre.OpNodeType.*
import eyre.gen.*

class Assembler(private val context: CompilerContext) {


	private val textWriter = context.textWriter

	private val dataWriter = context.dataWriter

	private val epilogueWriter = NativeWriter()

	private var writer = textWriter

	private var section = Section.TEXT

	private val groups = ManualParser("encodings.txt").let { it.read(); it.groups }

	private lateinit var group: EncGroup



	fun assemble() {
		for(srcFile in context.srcFiles) {
			for(node in srcFile.nodes) {
				when(node) {
					is InsNode       -> assemble(node)
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
			writer = dataWriter
			section = Section.DATA
			writer.align8()
			s.section = Section.DATA
			s.pos = writer.pos
			for(c in s.string) writer.i8(c.code)
			writer.i8(0)
		}
	}



	private fun invalid(): Nothing = error("Invalid encoding")



	/*
	Node assembly
	 */



	private inline fun sectioned(writer: NativeWriter, section: Section, block: () -> Unit) {
		val prevWriter = this.writer
		val prevSection = this.section
		this.writer = writer
		this.section = section
		block()
		this.writer = prevWriter
		this.section = prevSection
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
		val registers = ArrayList<Reg>()
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
				if(epilogueWriter.isEmpty)
					writer.i8(0xC3)
				else
					writer.bytes(epilogueWriter)
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



	private fun resolveImmRec(node: AstNode, regValid: Boolean): Long = when(node) {
		is IntNode      -> node.value
		is UnaryNode    -> node.calculate(::resolveImmRec, regValid)
		is BinaryNode   -> node.calculate(::resolveImmRec, regValid)
		//is StringNode -> node.value.ascii64()

		is SymNode -> when(val symbol = node.symbol) {
			is PosSymbol ->
				if(immRelocCount++ == 0 && !regValid)
					error("First relocation (absolute or relative) must be positive and absolute")
				else
					0L
			is IntSymbol -> symbol.intValue
			else -> invalid()
		}

		else -> error("Invalid imm node: $node")
	}



	private fun resolveImm(node: AstNode): Long {
		immRelocCount = 0
		return resolveImmRec(if(node is OpNode) node.node else node, true)
	}



	private fun writeImm(
		node  : AstNode,
		width : Width,
		imm64 : Boolean = false,
		rel   : Boolean = false
	) = writeImm(node, width, resolveImm(node), imm64, rel)



	private fun writeImm(
		node  : AstNode,
		width : Width,
		value : Long,
		imm64 : Boolean = false,
		rel   : Boolean = false
	) {
		var actualWidth = width

		if(node is OpNode) {
			if(!node.isImm) invalid()
			actualWidth = if(width == QWORD && !imm64) DWORD else width
			if(node.width != null && node.width != actualWidth) invalid()
		}

		if(rel) {
			if(hasImmReloc)
				addRelReloc(actualWidth, node, 0)
			writer.writeWidth(actualWidth, value)
		} else if(immRelocCount == 1) {
			if(!imm64 || width != QWORD)
				error("Absolute relocations are only allowed with 64-bit operands")
			addAbsReloc(node)
			writer.advance(8)
		} else if(hasImmReloc) {
			addLinkReloc(actualWidth, node)
			writer.advance(actualWidth.bytes)
		} else if(!writer.writeWidth(actualWidth, value)) {
			invalid()
		}
	}



	/*
	Memory operands
	 */



	private var baseReg: Reg? = null
	private var indexReg: Reg? = null
	private var indexScale = 0
	private var aso = 0 // 0 = none, 1 = 32, 2 = 64
	private var memRelocCount = 0
	private val hasMemReloc get() = memRelocCount > 0



	private fun checkAso(width: Width) = when(width) {
		DWORD -> if(aso == 2) invalid() else aso = 1
		QWORD -> if(aso == 1) invalid() else aso = 2
		else  -> invalid()
	}



	private fun resolveMemRec(node: AstNode, regValid: Boolean): Long = when(node) {
		is IntNode    -> node.value
		is UnaryNode  -> node.calculate(::resolveMemRec, regValid)
		is BinaryNode -> {
			val regNode = node.left as? RegNode ?: node.right as? RegNode
			val intNode = node.left as? IntNode ?: node.right as? IntNode
			if(node.op == BinaryOp.MUL && regNode != null && intNode != null) {
				if(indexReg != null && !regValid) invalid()
				checkAso(regNode.value.width)
				indexReg = regNode.value
				indexScale = intNode.value.toInt()
				0
			} else
				node.calculate(::resolveMemRec, regValid)
		}
		is RegNode -> {
			if(!regValid) invalid()
			checkAso(node.value.width)
			if(baseReg != null) {
				if(indexReg != null) invalid()
				indexReg = node.value
				indexScale = 1
			} else
				baseReg = node.value
			0
		}
		is SymNode -> when(val symbol = node.symbol) {
			is PosSymbol ->
				if(memRelocCount++ == 0 && !regValid)
					error("First relocation (absolute or relative) must be positive and absolute")
				else
					0L
			is IntSymbol -> symbol.intValue
			is VarAliasSymbol -> resolveMemRec((symbol.node as VarAliasNode).value, regValid)
			is AliasRefSymbol -> resolveMemRec(symbol.value, regValid) + symbol.offset
			null -> error("Unresolved symbol: $node")
			else -> invalid()
		}

		else -> error("Invalid mem node: $node")
	}



	private fun resolveMem(node: AstNode): Long {
		baseReg = null
		indexReg = null
		indexScale = 0
		aso = 0
		memRelocCount = 0

		val disp = resolveMemRec(node, true)

		// RSP and ESP cannot be index registers, swap to base if possible
		if(baseReg != null && indexReg != null && indexReg!!.value == 4) {
			if(indexScale != 1) invalid()
			val temp = indexReg
			indexReg = baseReg
			baseReg = temp
		}

		if(indexScale.countOneBits() > 1 || indexScale > 8)
			error("Invalid index: $indexScale")

		if(indexScale == 0)
			indexReg = null

		return disp
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
			invalid() // Empty memory operand
		}
	}




	/*
	Writing
	 */



	private val OpNode.asST get() = if(reg.type != RegType.ST) invalid() else reg
	
	private val OpNode.asReg get() = if(type != REG) invalid() else reg

	private val OpNode.asMem get() = if(type != MEM) invalid() else this

	private val OpNode.asImm get() = if(type != IMM) invalid() else this

	private fun byte(value: Int) = writer.i8(value)

	private fun word(value: Int) = writer.i16(value)

	private fun dword(value: Int) = writer.i32(value)

	/** [w]: 64-bit override [r]: REG, [x]: INDEX, [b]: RM, BASE, or OPREG */
	private fun writeRex(w: Int, r: Int, x: Int, b: Int) {
		val value = 0b0100_0000 or (w shl 3) or (r shl 2) or (x shl 1) or b
		if(value != 0b0100_0000) writer.i8(value)
	}

	/** [w]: 64-bit override [r]: REG, [x]: INDEX, [b]: RM, BASE, or OPREG */
	private fun writeRex(w: Int, r: Int, x: Int, b: Int, forced: Int, banned: Int) {
		val value = (w shl 3) or (r shl 2) or (x shl 1) or b
		if(forced == 1 || value != 0)
			if(banned == 1)
				invalid()
			else
				byte(0b0100_0000 or value)
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
	private fun rexw(enc: Enc, width: Width) =
		((width.bytes shr 3) and (enc.mask.value shr 2)) or enc.rexw

	private fun writeEscape(enc: Enc) {
		when(enc.escape) {
			Escape.NONE -> Unit
			Escape.E0F  -> writer.i8(0x0F)
			Escape.E38  -> writer.i16(0x380F)
			Escape.E3A  -> writer.i16(0x3A0F)
		}
	}

	private fun writeSseOpcode(enc: Enc) {
		writeEscape(enc)
		writer.i8(enc.opcode)
	}

	private fun writeOpcode(enc: Enc) {
		writeEscape(enc)
		if(enc.opcode and 0xFF00 != 0) word(enc.opcode) else byte(enc.opcode)
	}

	private fun writeOpcode(enc: Enc, width: Width) {
		writeEscape(enc)
		val addition2 = ((enc.mask.value and 1) and (1 shl width.ordinal).inv())
		if(enc.opcode and 0xFF00 != 0) {
			word(enc.opcode + (addition2 shl 3))
		} else {
			byte(enc.opcode + addition2)
		}
	}

	private fun writeOpcode(enc: Enc, addition: Int) {
		writeEscape(enc)
		if(enc.opcode and 0xFF00 != 0) {
			word(enc.opcode + (addition shl 3))
		} else {
			byte(enc.opcode + addition)
		}
	}

	private fun writeOpcode(enc: Enc, width: Width, addition: Int) {
		writeEscape(enc)
		val addition2 = addition + ((enc.mask.value and 1) and (1 shl width.ordinal).inv())
		if(enc.opcode and 0xFF00 != 0) {
			word(enc.opcode + (addition2 shl 3))
		} else {
			byte(enc.opcode + addition2)
		}
	}

	private fun checkMask(enc: Enc, width: Width) {
		if(width !in enc.mask) invalid()
	}

	private fun writeO16(enc: Enc, width: Width) {
		if(enc.o16 == 1 || (width == WORD && enc.mask != OpMask.WORD))
			writer.i8(0x66)
	}

	private fun writeA32() {
		if(aso == 1) byte(0x67)
	}

	private fun writePrefix(enc: Enc) {
		when(enc.prefix) {
			Prefix.NONE -> Unit
			Prefix.P66  -> writer.i8(0x66)
			Prefix.PF2  -> writer.i8(0xF2)
			Prefix.PF3  -> writer.i8(0xF3)
			Prefix.P9B  -> writer.i8(0x9B)
			Prefix.P67  -> writer.i8(0x67)
		}
	}



	/*
	Encoding
	 */



	private fun encodeNone(enc: Enc) {
		if(enc.o16 == 1) writer.i8(0x66)
		writePrefix(enc)
		if(enc.rexw == 1) writer.i8(0x48)
		writeOpcode(enc)
	}



	private fun encodeNone(enc: Enc, width: Width) {
		writeO16(enc, width)
		writePrefix(enc)
		writeRex(rexw(enc, width), 0, 0, 0)
		writeOpcode(enc, width)
	}



	private fun encode1R(enc: Enc, op1: Reg) {
		val width = op1.width
		checkMask(enc, width)
		writeO16(enc, width)
		writePrefix(enc)
		writeRex(rexw(enc, width), 0, 0, op1.rex, op1.rex8, op1.noRex)
		writeOpcode(enc, width)
		writeModRM(0b11, enc.extension, op1.value)
	}



	private fun encode1RA(enc: Enc, op1: Reg) {
		val width = op1.width
		checkMask(enc, width)
		if(width == DWORD) byte(0x67)
		writePrefix(enc)
		writeRex(0, 0, 0, op1.rex)
		writeOpcode(enc, width)
		writeModRM(0b11, enc.extension, op1.value)
	}



	private fun encode1O(enc: Enc, op1: Reg) {
		val width = op1.width
		checkMask(enc, width)
		writeO16(enc, width)
		writePrefix(enc)
		writeRex(rexw(enc, width), 0, 0, op1.rex, op1.rex8, op1.noRex)
		writeOpcode(enc, width, op1.value + if(op1.type != RegType.R8) 7 else 0)
	}



	private fun encode1ST(enc: Enc, op1: Reg) {
		writePrefix(enc)
		writeOpcode(enc, op1.value)
	}



	private fun encode1M(enc: Enc, op1: OpNode) {
		if(enc.mask.count <= 1) {
			if(op1.width != null && op1.width !in enc.mask) invalid()
			val disp = resolveMem(op1.node)
			writeA32()
			writePrefix(enc)
			writeRex(enc.rexw, 0, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
			writeOpcode(enc)
			writeMem(op1.node, enc.extension, disp, 0)
		} else {
			val width = op1.width ?: invalid()
			val disp = resolveMem(op1.node)
			writeA32()
			writeO16(enc, width)
			writePrefix(enc)
			writeRex(rexw(enc, width), 0, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
			writeOpcode(enc, width)
			writeMem(op1.node, enc.extension, disp, 0)
		}
	}


	private fun encode2RR(enc: Enc, op1: Reg, op2: Reg) {
		val width = op1.width
		checkMask(enc, width)
		writeO16(enc, width)
		writePrefix(enc)
		writeRex(rexw(enc, width), op1.rex, 0, op2.rex, op1.rex8 or op2.rex8, op1.noRex or op2.noRex)
		writeOpcode(enc, width)
		writeModRM(0b11, op1.value, op2.value)
	}


	private fun encode2RM(enc: Enc, op1: Reg, op2: OpNode) {
		val width = op1.width
		checkMask(enc, op1.width)
		val disp = resolveMem(op2.node)
		writeA32()
		writeO16(enc, width)
		writePrefix(enc)
		writeRex(rexw(enc, width), op1.rex, indexReg?.rex ?: 0, baseReg?.rex ?: 0, op1.rex8, op1.noRex)
		writeOpcode(enc, width)
		writeMem(op2.node, op1.value, disp, 0)
	}



	private fun encode2RAM(enc: Enc, op1: Reg, op2: OpNode) {
		val width = op1.width
		checkMask(enc, op1.width)
		val disp = resolveMem(op2.node)
		if(width == DWORD) byte(0x67)
		writeA32()
		writePrefix(enc)
		writeRex(0, op1.rex, indexReg?.rex ?: 0, baseReg?.rex ?: 0, op1.rex8, op1.noRex)
		writeOpcode(enc)
		writeMem(op2.node, op1.value, disp, 0)
	}



	private fun encodeMOVRR(op1: Reg, op2: Reg, opcode: Int) {
		if(group.mnemonic != Mnemonic.MOV) invalid()
		if(op2.type != RegType.R64) invalid()
		writeRex(0, op1.rex, 0, op2.rex)
		writer.varLengthInt(opcode)
		writeModRM(0b11, op1.value, op2.value)
	}



	private fun encodeMOVRSEG(op1: Reg, op2: Reg, r: Reg, opcode: Int) {
		if(group.mnemonic != Mnemonic.MOV) invalid()
		when(r.type) {
			RegType.R16 -> { byte(0x66); writeRex(0, op1.rex, 0, op2.rex) }
			RegType.R32 -> writeRex(0, op1.rex, 0, op2.rex)
			RegType.R64 -> writeRex(1, op1.rex, 0, op2.rex)
			else        -> invalid()
		}
		byte(opcode)
		writeModRM(0b11, op1.value, op2.value)
	}



	private fun encode1I(opcode: Int, width: Width, op1: OpNode) {
		writer.varLengthInt(opcode)
		writeImm(op1, width)
	}



	/*
	Assembly
	 */



	private val Ops.enc: Enc get() = if(this !in group) invalid() else group[this]

	private fun encoding(ops: SseOps): Enc {
		for(e in group.encs)
			if(e.sseOps == ops)
				return e
		invalid()
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



	private fun assemble(node: InsNode) {
		node.prefix?.let { byte(it.value) }

		if(node.mnemonic == Mnemonic.DLLCALL) {
			encodeDLLCALL(node)
			return
		} else if(node.mnemonic == Mnemonic.RETURN ){
			encodeRETURN(node)
			return
		}

		group = groups[node.mnemonic] ?: invalid()

		if(group.isSse)
			assembleSse(node)
		else
			assembleGp(node)
	}



	private fun encodeDLLCALL(node: InsNode) {
		if(node.size != 1 || node.op1!!.width != null) invalid()
		val op1 = node.op1.asImm.node as? NameNode ?: invalid()
		op1.symbol = context.getDllImport(op1.name)
		if(op1.symbol == null) error("Unrecognised dll import: ${op1.name}")
		encode1M(groups[Mnemonic.CALL]!![Ops.M], OpNode.mem(null, op1))
	}



	private fun encodeRETURN(node: InsNode) {
		if(node.size != 0) invalid()
		if(epilogueWriter.isEmpty)
			writer.i8(0xC3)
		else
			writer.bytes(epilogueWriter)
	}



	/*
	Sse Assembly
	 */



	private fun encodeSseRR(op1: Reg, op2: Reg, op3: OpNode?) {
		if(op1.high != 0 || op2.high != 0) invalid()

		val isImm = op3 != null && op3.isImm

		val enc = encoding(SseOps(op1.sseOp, op2.sseOp, if(isImm) SseOp.I8 else SseOp.NONE))

		if(enc.o16 == 1) byte(0x66)
		if(enc.prefix != Prefix.NONE) byte(enc.prefix.value)

		if(!enc.mr) {
			writeRex(enc.rexw, op1.rex, 0, op2.rex)
			writeSseOpcode(enc)
			writeModRM(0b11, op1.value, op2.value)
		} else {
			writeRex(enc.rexw, op2.rex, 0, op1.rex)
			writeSseOpcode(enc)
			writeModRM(0b11, op2.value, op1.value)
		}

		if(isImm)
			writeImm(op3!!, BYTE)
	}



	private fun encodeSseRM(op1: Reg, op2: OpNode, op3: OpNode?, reversed: Boolean) {
		if(op1.high != 0) invalid()
		val isImm = op3 != null && op3.isImm
		val enc: Enc

		if(group.mnemonic == Mnemonic.CVTSI2SD || group.mnemonic == Mnemonic.CVTSI2SS) {
			if(op1.type != RegType.X)
				invalid()
			enc = when(op2.width) {
				null,
				DWORD -> group.encs.first { it.mask == OpMask.DWORD }
				QWORD -> group.encs.first { it.mask == OpMask.QWORD }
				else  -> invalid()
			}
		} else {
			enc = if(!reversed)
				encoding(SseOps(op1.sseOp, SseOp.M, if(isImm) SseOp.I8 else SseOp.NONE))
			else
				encoding(SseOps(SseOp.M, op1.sseOp, if(isImm) SseOp.I8 else SseOp.NONE))
			if(op2.width != null && op2.width !in enc.mask)
				invalid()
		}

		val disp = resolveMem(op2.node)
		writeA32()
		if(enc.o16 == 1) byte(0x66)
		if(enc.prefix != Prefix.NONE) byte(enc.prefix.value)
		writeRex(enc.rexw, op1.rex, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
		writeSseOpcode(enc)
		writeMem(op2.node, op1.value, disp, 0)
		if(isImm) writeImm(op3!!.node, BYTE)
	}



	private fun encodeSseRI(op1: Reg, op2: OpNode, op3: OpNode?) {
		if(op3 != null) invalid()
		val enc = encoding(SseOps(op1.sseOp, SseOp.I8, SseOp.NONE))
		if(enc.o16 == 1) byte(0x66)
		if(enc.prefix != Prefix.NONE) byte(enc.prefix.value)
		writeRex(enc.rexw, op1.rex, 0, 0)
		writeSseOpcode(enc)
		writeModRM(0b11, enc.extension, op1.value)
		writeImm(op2, BYTE)
	}



	private fun assembleSse(node: InsNode) {
		val op1 = node.op1!!
		val op2 = node.op2!!

		when(op1.type) {
			REG -> when(op2.type) {
				REG -> encodeSseRR(op1.reg, op2.reg, node.op3)
				MEM -> encodeSseRM(op1.reg, op2, node.op3, false)
				IMM -> encodeSseRI(op1.reg, op2, node.op3)
			}
			MEM -> when(op2.type) {
				REG -> encodeSseRM(op2.reg, op1, node.op3, true)
				MEM -> invalid()
				IMM -> invalid()
			}
			IMM -> invalid()
		}
	}


	private fun assembleGp(node: InsNode) {
		val op1 = node.op1
		val op2 = node.op2
		val op3 = node.op3

		when(node.size) {
			0 -> encodeNone(Ops.NONE.enc)

			1 -> when(op1!!.type) {
				REG -> assemble1R(op1.reg)
				MEM -> assemble1M(op1)
				IMM -> assemble1I(op1)
			}

			2 -> when(op1!!.type) {
				REG -> when(op2!!.type) {
					REG -> assemble2RR(op1.reg, op2.reg)
					MEM -> assemble2RM(op1.reg, op2)
					IMM -> assemble2RI(op1.reg, op2)
				}
				MEM -> when(op2!!.type) {
					REG -> assemble2MR(op1, op2.reg)
					MEM -> invalid()
					IMM -> assemble2MI(op1, op2)
				}
				IMM -> when(op2!!.type) {
					REG -> assemble2IR(op1, op2.reg)
					IMM -> assemble2II(op1, op2)
					else -> invalid()
				}
			}

			3 -> when(group.mnemonic) {
				Mnemonic.IMUL -> assembleIMUL(op1!!.asReg, op2!!, op3!!.asImm)
				Mnemonic.SHLD -> assembleSHLD(op1!!, op2!!.asReg, op3!!)
				Mnemonic.SHRD -> assembleSHLD(op1!!, op2!!.asReg, op3!!)
				else -> invalid()
			}

			4 -> invalid()
		}
	}



	/*
	1-operand assembly
	 */



	private fun assemble1R(op1: Reg) {
		when {
			op1.isR -> when {
				Ops.RA in group -> encode1RA(Ops.RA.enc, op1)
				Ops.R in group  -> encode1R(Ops.R.enc, op1)
				Ops.O in group  -> encode1O(Ops.O.enc, op1)
				op1 == Reg.AX   -> encodeNone(Ops.AX.enc)
				else            -> invalid()
			}
			op1.isST      -> encode1ST(Ops.ST.enc, op1)
			op1 == Reg.FS -> encodeNone(Ops.FS.enc)
			op1 == Reg.GS -> encodeNone(Ops.GS.enc)
			else          -> invalid()
		}
	}



	private fun assemble1M(op1: OpNode) {
		encode1M(Ops.M.enc, op1)
	}



	private fun assemble1I(op1: OpNode) {
		val imm = resolveImm(op1)

		fun imm(ops: Ops, width: Width, rel: Boolean) {
			encodeNone(ops.enc)
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
			else              -> invalid()
		}
	}



	/*
	2-operand assembly
	 */



	private fun assemble2MR(op1: OpNode, op2: Reg) {
		when {
			op1.width != null && op1.width != op2.width -> invalid()
			op2.type == RegType.SEG -> encode2RM(Ops.M_SEG.enc, op2, op1)
			Ops.M_R in group -> encode2RM(Ops.M_R.enc, op2, op1)
			else -> invalid()
		}
	}



	private fun assemble2MI(op1: OpNode, op2: OpNode) {
		val imm = resolveImm(op2)

		fun encode(ops: Ops, width: Width) {
			encode1M(ops.enc, op1)
			writeImm(op2, width, imm)
		}

		when {
			Ops.RM_I8 in group -> when {
				!hasImmReloc && imm.isImm8 -> encode(Ops.RM_I8, BYTE)
				Ops.RM_I in group -> encode(Ops.RM_I, op1.width ?: invalid())
				else -> invalid()
			}
			Ops.RM_I in group -> encode(Ops.RM_I, op1.width ?: invalid())
		}
	}



	private fun assemble2IR(op1: OpNode, op2: Reg) {
		if(Ops.I8_A !in group) invalid()
		when(op2) {
			Reg.AL  -> encode1I(0xE6, BYTE, op1)
			Reg.AX  -> encode1I(0xE766, BYTE, op1)
			Reg.EAX -> encode1I(0xE7, BYTE, op1)
			else    -> invalid()
		}
	}



	private fun assemble2II(op1: OpNode, op2: OpNode) {
		val imm1 = resolveImm(op1)
		if(hasImmReloc || !imm1.isImm16) invalid()
		val imm2 = resolveImm(op2)
		if(hasImmReloc || !imm1.isImm8) invalid()
		encodeNone(Ops.I16_I8.enc)
		word(imm1.toInt())
		byte(imm2.toInt())
	}



	private fun assemble2RI(op1: Reg, op2: OpNode) {
		val imm = resolveImm(op2)

		fun encodeAI() {
			encodeNone(Ops.A_I.enc, op1.width)
			writeImm(op2, op1.width, imm)
		}

		fun encode(ops: Ops, width: Width) {
			encode1R(ops.enc, op1)
			writeImm(op2, width, imm)
		}

		when {
			Ops.O_I in group -> {
				encode1O(Ops.O_I.enc, op1)
				writeImm(op2, op1.width, imm, true)
			}
			Ops.RM_I8 in group -> when {
				!hasImmReloc && imm.isImm8 -> encode(Ops.RM_I8, BYTE)
				Ops.A_I in group -> encodeAI()
				Ops.RM_I in group -> encode(Ops.RM_I, op1.width)
				else -> invalid()
			}
			Ops.A_I in group -> encodeAI()
			Ops.RM_I in group -> encode(Ops.RM_I, op1.width)
			Ops.A_I8 in group -> when(op1) {
				Reg.AL  -> encode1I(0xE4, BYTE, op2)
				Reg.AX  -> encode1I(0xE566, BYTE, op2)
				Reg.EAX -> encode1I(0xE5, BYTE, op2)
				else    -> invalid()
			}
			else -> invalid()
		}
	}



	private fun assemble2RM(op1: Reg, op2: OpNode) {
		when {
			Ops.R_MEM in group -> encode2RM(Ops.R_MEM.enc, op1, op2)

			op2.width != null && op1.width != op2.width -> when(op2.width) {
				BYTE  -> encode2RM(Ops.R_RM8.enc, op1, op2)
				WORD  -> encode2RM(Ops.R_RM16.enc, op1, op2)
				DWORD -> encode2RM(Ops.R_RM32.enc, op1, op2)
				XWORD -> encode2RM(Ops.R_M128.enc, op1, op2)
				ZWORD -> encode2RAM(Ops.RA_M512.enc, op1, op2)
				else  -> invalid()
			}

			op1.type == RegType.SEG -> encode2RM(Ops.SEG_M.enc, op1, op2)
			Ops.R_M in group -> encode2RM(Ops.R_M.enc, op1, op2)

			Ops.R_M128 in group -> encode2RM(Ops.R_M128.enc, op1, op2)
			Ops.RA_M512 in group -> encode2RAM(Ops.RA_M512.enc, op1, op2)

			else -> invalid()
		}
	}



	private fun assemble2RR(op1: Reg, op2: Reg) {
		when {
			op1.isR && op2.isR -> when {
				op2 == Reg.CL && Ops.RM_CL in group -> encode1R(Ops.RM_CL.enc, op1)

				op1.width != op2.width -> when {
					Ops.A_DX in group -> when {
						!op1.isA ||
						op2 != Reg.DX  -> invalid()
						op1 == Reg.AL  -> byte(0xEC)
						op1 == Reg.AX  -> word(0xED66)
						op1 == Reg.EAX -> byte(0xED)
						else           -> invalid()
					}

					Ops.DX_A in group -> when {
						!op2.isA ||
						op1 != Reg.DX  -> invalid()
						op2 == Reg.AL  -> byte(0xEE)
						op2 == Reg.AX  -> word(0xEF66)
						op2 == Reg.EAX -> byte(0xEF)
						else           -> invalid()
					}

					else -> when(op2.width) {
						BYTE  -> encode2RR(Ops.R_RM8.enc, op1, op2)
						WORD  -> encode2RR(Ops.R_RM16.enc, op1, op2)
						DWORD -> encode2RR(Ops.R_RM32.enc, op1, op2)
						else  -> invalid()
					}
				}

				Ops.A_O in group -> when {
					op1.isA -> encode1O(Ops.A_O.enc, op2)
					op2.isA -> encode1O(Ops.A_O.enc, op1)
					else    -> encode2RR(Ops.R_R.enc, op1, op2)
				}

				Ops.M_R in group -> encode2RR(Ops.R_R.enc, op2, op1)
				Ops.R_R in group -> encode2RR(Ops.R_R.enc, op1, op2)

				else -> invalid()
			}

			// FPU
			op1 == Reg.ST0 -> encode1ST(Ops.ST0_ST.enc, op2)
			op2 == Reg.ST0 -> encode1ST(Ops.ST_ST0.enc, op1)

			// MOV
			op1.type == RegType.CR -> encodeMOVRR(op1, op2, 0x220F)
			op2.type == RegType.CR -> encodeMOVRR(op2, op1, 0x200F)
			op1.type == RegType.DR -> encodeMOVRR(op1, op2, 0x230F)
			op2.type == RegType.DR -> encodeMOVRR(op2, op1, 0x210F)
			op1.type == RegType.SEG -> encodeMOVRSEG(op1, op2, op2, 0x8E)
			op2.type == RegType.SEG -> encodeMOVRSEG(op2, op1, op1, 0x8C)

			else -> invalid()
		}
	}



	/*
	3-operand assembly
	 */



	private fun assembleIMUL(op1: Reg, op2: OpNode, op3: OpNode) {
		val imm = resolveImm(op3)

		val width: Width
		val ops: Ops

		if(!hasImmReloc && imm.isImm8) {
			ops =Ops.R_RM_I8
			width = BYTE
		} else {
			ops = Ops.R_RM_I
			width = op1.width
		}

		if(op2.width != null && op1.width != op2.width) invalid()

		when(op2.type) {
			REG -> encode2RR(ops.enc, op1, op2.reg)
			MEM -> encode2RM(ops.enc, op1, op2)
			IMM -> invalid()
		}

		writeImm(op3, width, imm)
	}



	private fun assembleSHLD(op1: OpNode, op2: Reg, op3: OpNode) {
		if(op1.width != null && op1.width != op2.width) invalid()

		when(op3.type) {
			REG -> {
				if(op3.reg != Reg.CL) invalid()
				when(op1.type) {
					REG -> encode2RR(Ops.RM_R_CL.enc, op2, op1.reg)
					MEM -> encode2RM(Ops.RM_R_CL.enc, op2, op1)
					IMM -> invalid()
				}
			}
			IMM -> {
				when(op1.type) {
					REG -> encode2RR(Ops.RM_R_I8.enc, op2, op1.reg)
					MEM -> encode2RM(Ops.RM_R_I8.enc, op2, op1)
					IMM -> invalid()
				}
				writeImm(op3, BYTE)
			}
			else -> invalid()
		}
	}


}