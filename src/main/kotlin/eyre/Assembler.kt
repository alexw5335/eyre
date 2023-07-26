package eyre

import eyre.util.NativeWriter
import eyre.Width.*
import eyre.OpNodeType.*
import eyre.Mnemonic.*
import eyre.gen.*

class Assembler(private val context: CompilerContext) {


	private val textWriter = context.textWriter

	private val dataWriter = context.dataWriter

	private val epilogueWriter = NativeWriter()

	private var writer = textWriter

	private var section = Section.TEXT

	private lateinit var currentIns: InsNode



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
			writer = dataWriter
			section = Section.DATA
			writer.align8()
			s.section = Section.DATA
			s.pos = writer.pos
			for(c in s.string) writer.i8(c.code)
			writer.i8(0)
		}
	}



	private fun invalid(): Nothing = buildString {
		append("Assembler error: ")
		append(currentIns.printString)
		//getEnc(currentIns)?.let { append("  ::  $it") }
	}.let(::error)

	private fun invalid(string: String): Nothing = error("$string (${currentIns.printString})")



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



	fun assembleForTesting(node: InsNode): Pair<Int, Int> {
		val start = writer.pos
		handleInstruction(node)
		return Pair(start, writer.pos - start)
	}



	private fun handleInstruction(node: InsNode) {
		currentIns = node

		when(node.size) {
			0 -> assemble0(node)
			1 -> assemble1(node, node.op1)
			2 -> assemble2(node, node.op1, node.op2)
			3 -> assemble3(node, node.op1, node.op2, node.op3)
			else -> invalid()
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
			writeImmBase(node, width)
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
		is UnaryNode    -> node.calculate(regValid, ::resolveImmRec)
		is BinaryNode   -> node.calculate(regValid, ::resolveImmRec)
		//is StringNode -> node.value.ascii64()

		is SymNode -> when(val symbol = node.symbol) {
			is PosSymbol ->
				if(immRelocCount++ == 0 && !regValid)
					invalid("First relocation (absolute or relative) must be positive and absolute")
				else
					0L
			is IntSymbol -> symbol.intValue
			else -> invalid()
		}

		else -> invalid("Invalid imm node: $node (${node.printString})")
	}



	private fun resolveImm(node: AstNode): Long {
		immRelocCount = 0
		return resolveImmRec(if(node is OpNode && node.isImm) node.node else node, true)
	}

	private fun writeRel(node: OpNode, width: Width, value: Long = resolveImm(node.node)) {
		if(node.width != null && node.width != width)
			invalid()
		if(hasImmReloc)
			addRelReloc(width, node.node, 0)
		writer.writeWidth(width, value)
	}

	private fun writeImmBase(node: AstNode, width: Width, value: Long = resolveImm(node)) {
		if(immRelocCount == 1) {
			if(width != QWORD) invalid()
			addAbsReloc(node)
			writer.advance(8)
		} else if(hasImmReloc) {
			addLinkReloc(width, node)
			writer.advance(width.bytes)
		} else if(!writer.writeWidth(width, value)) {
			invalid()
		}
	}

	private fun writeImm(node: OpNode, width: Width, value: Long = resolveImm(node.node)) {
		if(node.width != null && node.width != width) invalid()
		writeImmBase(node.node, width, value)
	}

	private fun Any.rel(node: OpNode, width: Width, value: Long = resolveImm(node.node)) =
		writeRel(node, width, value)

	private fun Any.imm(node: OpNode, width: Width, value: Long = resolveImm(node.node)) =
		writeImm(node, width, value)

	private fun Any.imm8(node: OpNode, value: Long = resolveImm(node.node)) =
		writeImm(node, BYTE, value)



	/*
	Memory operands
	 */



	inner class Mem {
		var node = NullNode
		var scale = 0
		var index = Reg.RAX
		var base = Reg.RAX
		var aso = 0
		var relocs = 0
		var hasBase = false
		var hasIndex = false
		var vsib = 0
		var disp = 0L
		var width: Width? = null

		fun reset() {
			node = NullNode
			resetBase()
			resetIndex()
			scale = 0
			aso = 0
			relocs = 0
			vsib = 0
			disp = 0L
			width = null
		}

		val rexX get() = index.rex
		val rexB get() = base.rex
		val vexX get() = index.vexRex
		val vexB get() = base.vexRex
		val hasReloc get() = relocs != 0

		fun resetBase() { hasBase = false; base = Reg.RAX }
		fun resetIndex() { hasIndex = false; index = Reg.RAX }
		fun assignBase(reg: Reg) { hasBase = true; base = reg }
		fun assignIndex(reg: Reg) { hasIndex = true; index = reg }
		fun swapBaseIndex() { val temp = index; index = base; base = temp }

		fun checkReg(reg: Reg) {
			when(reg.type) {
				RegType.R32 -> if(aso == 2) invalid() else aso = 1
				RegType.R64 -> if(aso == 1) invalid() else aso = 2
				RegType.X   -> vsib = 1
				RegType.Y   -> vsib = 2
				RegType.Z   -> vsib = 3
				else        -> invalid()
			}
		}

	}



	private fun resolveMemRec(node: AstNode, mem: Mem, regValid: Boolean): Long {
		if(node is IntNode)
			return node.value
		if(node is UnaryNode)
			return node.calculate(regValid) { n, v -> resolveMemRec(n, mem, v) }
		if(node is BinaryNode) {
			val regNode = node.left as? RegNode ?: node.right as? RegNode
			val intNode = node.left as? IntNode ?: node.right as? IntNode
			if(node.op == BinaryOp.MUL && regNode != null && intNode != null) {
				if(mem.hasIndex && !regValid) invalid()
				mem.checkReg(regNode.value)
				mem.assignIndex(regNode.value)
				mem.scale = intNode.value.toInt()
				return 0
			}
			return node.calculate(regValid) { n, v -> resolveMemRec(n, mem, v) }
		}
		if(node is RegNode) {
			if(!regValid) invalid()
			mem.checkReg(node.value)
			if(mem.hasBase) {
				if(mem.hasIndex) invalid()
				mem.assignIndex(node.value)
				mem.scale = 1
			} else {
				mem.assignBase(node.value)
			}
			return 0
		}
		if(node is SymNode) {
			val symbol = node.symbol
			if(symbol is PosSymbol)
				if(mem.relocs++ == 0 && !regValid)
					invalid("First relocation (absolute or relative) must be positive and absolute")
				else
					return 0L
			if(symbol is IntSymbol) return symbol.intValue
			if(symbol is VarAliasSymbol) return resolveMemRec((symbol.node as VarAliasNode).value, mem, regValid)
			if(symbol is AliasRefSymbol) return resolveMemRec(symbol.value, mem, regValid) + symbol.offset
			if(symbol == null) invalid("Unresolved symbol: $node")
			invalid("Invalid symbol: $symbol")
		}
		error("Invalid mem node: $node")
	}



	private fun Mem.postResolve() {
		if(vsib != 0) {
			if(!index.isV) {
				if(hasIndex) {
					if(scale != 1) invalid()
					swapBaseIndex()
				} else {
					assignIndex(base)
					resetBase()
				}
			}
			if(scale.countOneBits() > 1 || scale > 8) invalid()
			return
		}

		// Index cannot be ESP/RSP, swap to base if possible
		if(hasIndex && index.isInvalidIndex) {
			when {
				scale != 1 -> invalid()
				hasBase    -> swapBaseIndex()
				else       -> { assignBase(index); resetIndex() }
			}
		} else if(hasIndex && base.value == 5 && scale == 1 && index.value != 5) {
			swapBaseIndex()
		}

		// 1: [R*1] -> [R], avoid SIB
		// 2: [R*2] -> [R+R*1], avoid index-only SIB which produces DISP32 of zero
		// 3: [R*3] -> [R+R*2], [R+R*3] -> invalid
		// 5: [R*5] -> [R+R*4], [R+R*5] -> invalid
		when(scale) {
			0 -> resetIndex()
			1 -> if(!hasBase) { assignBase(index); resetIndex() }
			2 -> if(!hasBase) { scale = 1; assignBase(index) }
			3 -> if(!hasBase) { scale = 2; assignBase(index) } else invalid()
			4 -> { }
			5 -> if(!hasBase) { scale = 4; assignBase(index) } else invalid()
			6 -> invalid()
			7 -> invalid()
			8 -> { }
			else -> invalid("Invalid SIB scale: $scale")
		}
	}



	private fun resolveMem(node: OpNode): Mem {
		val mem = Mem()
		mem.disp = resolveMemRec(node.node, mem, true)
		mem.postResolve()
		mem.width = node.width
		return mem
	}



	private fun Mem.write(reg: Int, immLength: Int) {
		fun relocAndDisp(mod: Int) {
			when {
				hasReloc -> { addLinkReloc(DWORD, node); writer.i32(0) }
				mod == 1 -> writer.i8(disp.toInt())
				mod == 2 -> writer.i32(disp.toInt())
			}
		}

		val mod = when {
			hasReloc    -> 2 // disp32, can't be sure of size
			disp == 0L  -> 0 // no disp
			disp.isImm8 -> 1 // disp8
			else        -> 2 // disp32
		}

		if(hasIndex) { // SIB: [R*S] or [R*S+DISP] or [R+R*S] or [R+R*S+DISP]
			if(hasBase) {
				if(base.value == 5 && mod == 0) { // [RBP/R13+R*S] -> [RBP+R*S+DISP8]
					writeModRM(0b01, reg, 0b100)
					writeSib(scale.countTrailingZeroBits(), index.value, 0b101)
					byte(0)
				} else {
					writeModRM(mod, reg, 0b100)
					writeSib(scale.countTrailingZeroBits(), index.value, base.value)
					relocAndDisp(mod)
				}
			} else { // Index only, requires disp32
				writeModRM(0, reg, 0b100)
				writeSib(scale.countTrailingZeroBits(), index.value, 0b101)
				relocAndDisp(0b10)
			}
		} else if(hasBase) { // Indirect: [R] or [R+DISP]
			if(base.value == 4) { // [RSP/R12] -> [RSP/R12+NONE*1] (same with DISP)
				writeModRM(mod, reg, 0b100)
				byte(0b00_100_100)
				relocAndDisp(mod)
			} else if(base.value == 5 && mod == 0) { // [RBP/R13] -> [RBP/R13+0]
				word(0b00000000_01_000_101 or (reg shl 3))
			} else {
				writeModRM(mod, reg, base.value)
				relocAndDisp(mod)
			}
		} else if(relocs and 1 == 1) { // RIP-relative (odd reloc count)
			writeModRM(0b00, reg, 0b101)
			addRelReloc(DWORD, node, immLength)
			dword(0)
		} else { // Absolute 32-bit or empty memory operand
			word(0b00_100_101_00_000_100 or (reg shl 3))
			relocAndDisp(0b10)
		}
	}



	/*
	Writing
	 */



	inline fun Enc(block: Enc.Companion.() -> Int) = Enc(block(Enc))

	private fun OpNode.immWidth() = when(width) {
		null  -> invalid()
		QWORD -> DWORD
		else  -> width
	}

	private fun Reg.immWidth() = when(width) {
		QWORD -> DWORD
		else -> width
	}

	private val OpNode.asST get() = if(reg.type != RegType.ST) invalid() else reg
	
	private val OpNode.asReg get() = if(type != REG) invalid() else reg

	private val OpNode.asMem get() = if(type != MEM) invalid() else this

	private val OpNode.asImm get() = if(type != IMM) invalid() else this

	private fun byte(value: Int) = writer.i8(value)
	
	private fun word(value: Int) = writer.i16(value)

	private fun i24(value: Int) = writer.i24(value)

	private fun dword(value: Int) = writer.i32(value)

	/** [w]: 64-bit override [r]: REG, [x]: INDEX, [b]: RM, BASE, or OPREG */
	private fun writeRex(w: Int, r: Int, x: Int, b: Int) {
		val value = 0b0100_0000 or (w shl 3) or (r shl 2) or (x shl 1) or b
		if(value != 0b0100_0000) byte(value)
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

	private fun checkWidth(mask: OpMask, width: Width) {
		if(width !in mask) invalid()
	}

	private fun writeO16(mask: OpMask, width: Width) {
		if(mask.value != 0b10 && width == WORD) writer.i8(0x66)
	}

	private fun writeA32(mem: Mem) {
		if(mem.vsib != 0) invalid("VSIB not valid here")
		if(mem.aso == 1) byte(0x67)
	}

	private fun writeModRM(mod: Int, reg: Int, rm: Int) {
		writer.i8((mod shl 6) or (reg shl 3) or rm)
	}

	private fun writeSib(scale: Int, index: Int, base: Int) {
		writer.i8((scale shl 6) or (index shl 3) or base)
	}

	/** Return 1 if width is QWORD and widths has DWORD set, otherwise 0 */
	private fun rexw(mask: OpMask, width: Width) =
		(width.bytes shr 3) and (mask.value shr 2)

	/** Add one if width is not BYTE and if widths has BYTE set */
	private fun getOpcode(opcode: Int, mask: OpMask, width: Width) =
		opcode + ((mask.value and 1) and (1 shl width.ordinal).inv())

	private fun writePrefix(enc: Enc) { when(enc.prefix) {
		0 -> { }
		1 -> byte(0x66)
		2 -> byte(0xF2)
		3 -> byte(0xF3)
		4 -> byte(0x9B)
	} }

	private fun writeEscape(enc: Enc) { when(enc.escape) {
		0 -> { }
		1 -> byte(0x0F)
		2 -> word(0x380F)
		3 -> word(0x3A0F)
	} }

	private fun writeOpcode(enc: Enc, mask: OpMask, width: Width) {
		writeEscape(enc)
		writer.varLengthInt(getOpcode(enc.opcode, mask, width))
	}

	private fun writeOpcode(enc: Enc) {
		writeEscape(enc)
		writer.varLengthInt(enc.opcode)
	}



	/*
	Encoding
	 */



	private fun Enc.encodeNone(width: Width) {
		checkWidth(mask, width)
		writeO16(mask, width)
		writePrefix(this)
		if(rexw(mask, width) == 1) writer.i8(0x48)
		writeOpcode(this, mask, width)
	}

	private fun Enc.encode1MEM(op1: OpNode) {
		if(!op1.isMem) invalid()
		if(op1.width != null) invalid()
		val mem = resolveMem(op1)
		writeA32(mem)
		writePrefix(this)
		writeRex(rexw, 0, mem.rexX, mem.rexB)
		writeOpcode(this)
		mem.write(ext, 0)
	}

	/** Used by FPU encodings whose widths have already been checked */
	private fun encode1MAny(opcode: Int, ext: Int, op1: OpNode) {
		val mem = resolveMem(op1)
		writeA32(mem)
		writeRex(0, 0, mem.rexX, mem.rexB)
		writer.varLengthInt(opcode)
		mem.write(ext, 0)
	}

	private fun Enc.encode1M8(op1: OpNode) = encode1MSingle(op1, BYTE)
	private fun Enc.encode1M16(op1: OpNode) = encode1MSingle(op1, WORD)
	private fun Enc.encode1M32(op1: OpNode) = encode1MSingle(op1, DWORD)
	private fun Enc.encode1M64(op1: OpNode) = encode1MSingle(op1, QWORD)

	
	private fun Enc.encode1MSingle(op1: OpNode, width: Width) {
		if(!op1.isMem) invalid()
		if(op1.width != null && width != op1.width) invalid()
		val mem = resolveMem(op1)
		writeA32(mem)
		writePrefix(this)
		writeRex(rexw, 0, mem.rexX, mem.rexB)
		writeOpcode(this)
		mem.write(ext, 0)
	}

	private fun Enc.encode1M(op1: OpNode, immLength: Int) {
		val mask = this.mask
		val mem = resolveMem(op1)

		if(mask.value.countOneBits() == 1) {
			writeA32(mem)
			writePrefix(this)
			writeRex(rexw, 0, mem.rexX, mem.rexB)
			writeEscape(this)
			writer.varLengthInt(opcode)
			mem.write(ext, immLength)
		} else {
			val width = op1.width ?: invalid()
			checkWidth(mask, width)
			writeO16(mask, width)
			writeA32(mem)
			writePrefix(this)
			writeRex(rexw or rexw(mask, width), 0, mem.rexX, mem.rexB)
			writeOpcode(this, mask, width)
			mem.write(ext, immLength)
		}
	}

	private fun Enc.encode1R(op1: Reg) {
		val mask = this.mask
		val width = op1.width
		checkWidth(mask, width)
		writeO16(mask, width)
		writePrefix(this)
		writeRex(rexw or rexw(mask, width), 0, 0, op1.rex, op1.rex8, op1.noRex)
		writeOpcode(this, mask, width)
		writeModRM(0b11, this.ext, op1.value)
	}

	private fun Enc.encode1O(op1: Reg) {
		val mask = this.mask
		val width = op1.width
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(rexw or rexw(mask, width), 0, 0, op1.rex)
		writeEscape(this)
		// Only single-byte opcodes use this encoding
		val opcode = this.opcode + (((mask.value and 1) and (1 shl width.ordinal).inv()) shl 3) + op1.value
		byte(opcode)
	}

	private fun Enc.encode2RMEM(op1: Reg, op2: OpNode) {
		if(op2.width != null) invalid()
		encode2RM(op1, op2, 0)
	}

	private fun Enc.encode1RA(op1: Reg) {
		if(op1.type == RegType.R32) byte(0x67) else if(op1.type != RegType.R64) invalid()
		writePrefix(this)
		writeRex(0, 0, 0, op1.rex)
		writeOpcode(this)
		writeModRM(0b11, this.ext, op1.value)
	}

	private fun Enc.encode2RAM512(op1: Reg, op2: OpNode) {
		if(op2.width != null && op2.width != ZWORD) invalid()
		if(op1.type == RegType.R32) byte(0x67)else if(op1.type != RegType.R64) invalid()
		val mem = resolveMem(op2)
		writeA32(mem)
		writePrefix(this)
		writeRex(0, op1.rex, mem.rexX, mem.rexB)
		writeOpcode(this)
		mem.write(op1.value, 0)
	}

	private fun Enc.encode2RM(op1: Reg, op2: OpNode, immLength: Int) {
		val mask = this.mask
		val width = op1.width
		if(mm == 0 && op2.width != null && op2.width != op1.width) invalid()
		val mem = resolveMem(op2)
		checkWidth(mask, width)
		writeO16(mask, width)
		writeA32(mem)
		writePrefix(this)
		writeRex(rexw or rexw(mask, width), op1.rex, mem.rexX, mem.rexB, op1.rex8, op1.noRex)
		writeOpcode(this, mask, width)
		mem.write(op1.value, immLength)
	}

	private fun Enc.encode2RR(op1: Reg, op2: Reg) {
		val mask = this.mask
		val width = op1.width
		if(mm == 0 && op1.width != op2.width) invalid()
		checkWidth(mask, width)
		writeO16(mask, width)
		writePrefix(this)
		writeRex(rexw or rexw(mask, width), op1.rex, 0, op2.rex, op1.rex8 or op2.rex8, op1.noRex or op2.noRex)
		writeOpcode(this, mask, width)
		writeModRM(0b11, op1.value, op2.value)
	}

	private fun Enc.encode1RM(op1: OpNode, immLength: Int) {
		when(op1.type) {
			REG -> encode1R(op1.reg)
			MEM -> encode1M(op1, immLength)
			IMM -> invalid()
		}
	}

	private fun Enc.encode2RRM(op1: Reg, op2: OpNode, immLength: Int) {
		when(op2.type) {
			REG  -> encode2RR(op1, op2.reg)
			MEM  -> encode2RM(op1, op2, immLength)
			else -> invalid()
		}
	}

	private fun Enc.encode2RMR(op1: OpNode, op2: Reg, immLength: Int) {
		when(op1.type) {
			REG  -> encode2RR(op2, op1.reg)
			MEM  -> encode2RM(op2, op1, immLength)
			else -> invalid()
		}
	}



	/*
	Assembly
	 */



	private fun assemble0(node: InsNode) { when(node.mnemonic) {
		INSB        -> writer.i8(0x6C)
		INSW        -> writer.i16(0x6D66)
		INSD        -> writer.i8(0x6D)
		OUTSB       -> writer.i8(0x6E)
		OUTSW       -> writer.i16(0x6F66)
		OUTSD       -> writer.i8(0x6F)
		NOP         -> writer.i8(0x90)
		PAUSE       -> writer.i16(0x90F3)
		CBW         -> writer.i16(0x9866)
		CWDE        -> writer.i8(0x98)
		CDQE        -> writer.i16(0x9848)
		CWD         -> writer.i16(0x9966)
		CDQ         -> writer.i8(0x99)
		CQO         -> writer.i16(0x9948)
		WAIT        -> writer.i8(0x9B)
		FWAIT       -> writer.i8(0x9B)
		PUSHFW      -> writer.i16(0x9C66)
		PUSHF       -> writer.i8(0x9C)
		PUSHFQ      -> writer.i8(0x9C)
		POPFW       -> writer.i16(0x9D66)
		POPF        -> writer.i8(0x9D)
		POPFQ       -> writer.i8(0x9D)
		SAHF        -> writer.i8(0x9E)
		LAHF        -> writer.i8(0x9F)
		MOVSB       -> writer.i8(0xA4)
		MOVSW       -> writer.i16(0xA566)
		MOVSD       -> writer.i8(0xA5)
		MOVSQ       -> writer.i16(0xA548)
		CMPSB       -> writer.i8(0xA6)
		CMPSW       -> writer.i16(0xA766)
		CMPSD       -> writer.i8(0xA7)
		CMPSQ       -> writer.i16(0xA748)
		STOSB       -> writer.i8(0xAA)
		STOSW       -> writer.i16(0xAB66)
		STOSD       -> writer.i8(0xAB)
		STOSQ       -> writer.i16(0xAB48)
		LODSB       -> writer.i8(0xAC)
		LODSW       -> writer.i16(0xAD66)
		LODSD       -> writer.i8(0xAD)
		LODSQ       -> writer.i16(0xAD48)
		SCASB       -> writer.i8(0xAE)
		SCASW       -> writer.i16(0xAF66)
		SCASD       -> writer.i8(0xAF)
		SCASQ       -> writer.i16(0xAF48)
		RET         -> writer.i8(0xC3)
		RETW        -> writer.i16(0xC366)
		RETF        -> writer.i8(0xCB)
		RETFQ       -> writer.i16(0xCB48)
		LEAVE       -> writer.i8(0xC9)
		LEAVEW      -> writer.i16(0xC966)
		INT3        -> writer.i8(0xCC)
		INT1        -> writer.i8(0xF1)
		ICEBP       -> writer.i8(0xF1)
		IRET        -> writer.i8(0xCF)
		IRETW       -> writer.i16(0xCF66)
		IRETD       -> writer.i8(0xCF)
		IRETQ       -> writer.i16(0xCF48)
		XLAT        -> writer.i8(0xD7)
		XLATB       -> writer.i8(0xD7)
		LOOPNZ      -> writer.i8(0xE0)
		LOOPNE      -> writer.i8(0xE0)
		LOOPZ       -> writer.i8(0xE1)
		LOOPE       -> writer.i8(0xE1)
		LOOP        -> writer.i8(0xE2)
		HLT         -> writer.i8(0xF4)
		CMC         -> writer.i8(0xF5)
		CLC         -> writer.i8(0xF8)
		STC         -> writer.i8(0xF9)
		CLI         -> writer.i8(0xFA)
		STI         -> writer.i8(0xFB)
		CLD         -> writer.i8(0xFC)
		STD         -> writer.i8(0xFD)
		F2XM1       -> writer.i16(0xF0D9)
		FABS        -> writer.i16(0xE1D9)
		FADD        -> writer.i16(0xC1DE)
		FADDP       -> writer.i16(0xC1DE)
		FCHS        -> writer.i16(0xE0D9)
		FCLEX       -> writer.i24(0xE2DB9B)
		FCMOVB      -> writer.i16(0xC1DA)
		FCMOVBE     -> writer.i16(0xD1DA)
		FCMOVE      -> writer.i16(0xC9DA)
		FCMOVNB     -> writer.i16(0xC1DB)
		FCMOVNBE    -> writer.i16(0xD1DB)
		FCMOVNE     -> writer.i16(0xC9DB)
		FCMOVNU     -> writer.i16(0xD9DB)
		FCMOVU      -> writer.i16(0xD9DA)
		FCOM        -> writer.i16(0xD1D8)
		FCOMI       -> writer.i16(0xF1DB)
		FCOMIP      -> writer.i16(0xF1DF)
		FCOMP       -> writer.i16(0xD9D8)
		FCOMPP      -> writer.i16(0xD9DE)
		FCOS        -> writer.i16(0xFFD9)
		FDECSTP     -> writer.i16(0xF6D9)
		FDISI       -> writer.i24(0xE1DB9B)
		FDIV        -> writer.i16(0xF9DE)
		FDIVP       -> writer.i16(0xF9DE)
		FDIVR       -> writer.i16(0xF1DE)
		FDIVRP      -> writer.i16(0xF1DE)
		FENI        -> writer.i24(0xE0DB9B)
		FFREE       -> writer.i16(0xC1DD)
		FINCSTP     -> writer.i16(0xF7D9)
		FINIT       -> writer.i24(0xE3DB9B)
		FLD         -> writer.i16(0xC1D9)
		FLD1        -> writer.i16(0xE8D9)
		FLDL2E      -> writer.i16(0xEAD9)
		FLDL2T      -> writer.i16(0xE9D9)
		FLDLG2      -> writer.i16(0xECD9)
		FLDLN2      -> writer.i16(0xEDD9)
		FLDPI       -> writer.i16(0xEBD9)
		FLDZ        -> writer.i16(0xEED9)
		FMUL        -> writer.i16(0xC9DE)
		FMULP       -> writer.i16(0xC9DE)
		FNCLEX      -> writer.i16(0xE2DB)
		FNDISI      -> writer.i16(0xE1DB)
		FNENI       -> writer.i16(0xE0DB)
		FNINIT      -> writer.i16(0xE3DB)
		FNOP        -> writer.i16(0xD0D9)
		FPATAN      -> writer.i16(0xF3D9)
		FPREM       -> writer.i16(0xF8D9)
		FPREM1      -> writer.i16(0xF5D9)
		FPTAN       -> writer.i16(0xF2D9)
		FRNDINT     -> writer.i16(0xFCD9)
		FSCALE      -> writer.i16(0xFDD9)
		FSETPM      -> writer.i16(0xE4DB)
		FSIN        -> writer.i16(0xFED9)
		FSINCOS     -> writer.i16(0xFBD9)
		FSQRT       -> writer.i16(0xFAD9)
		FST         -> writer.i16(0xD1DD)
		FSTP        -> writer.i16(0xD9DD)
		FSUB        -> writer.i16(0xE9DE)
		FSUBP       -> writer.i16(0xE9DE)
		FSUBR       -> writer.i16(0xE1DE)
		FSUBRP      -> writer.i16(0xE1DE)
		FTST        -> writer.i16(0xE4D9)
		FUCOM       -> writer.i16(0xE1DD)
		FUCOMI      -> writer.i16(0xE9DB)
		FUCOMIP     -> writer.i16(0xE9DF)
		FUCOMP      -> writer.i16(0xE9DD)
		FUCOMPP     -> writer.i16(0xE9DA)
		FXAM        -> writer.i16(0xE5D9)
		FXCH        -> writer.i16(0xC9D9)
		FXTRACT     -> writer.i16(0xF4D9)
		FYL2X       -> writer.i16(0xF1D9)
		FYL2XP1     -> writer.i16(0xF9D9)
		ENCLV       -> writer.i24(0xC0010F)
		VMCALL      -> writer.i24(0xC1010F)
		VMLAUNCH    -> writer.i24(0xC2010F)
		VMRESUME    -> writer.i24(0xC3010F)
		VMXOFF      -> writer.i24(0xC4010F)
		CLAC        -> writer.i24(0xCA010F)
		STAC        -> writer.i24(0xCB010F)
		PCONFIG     -> writer.i24(0xC5010F)
		WRMSRNS     -> writer.i24(0xC6010F)
		MONITOR     -> writer.i24(0xC8010F)
		MWAIT       -> writer.i24(0xC9010F)
		ENCLS       -> writer.i24(0xCF010F)
		XGETBV      -> writer.i24(0xD0010F)
		XSETBV      -> writer.i24(0xD1010F)
		VMFUNC      -> writer.i24(0xD4010F)
		XEND        -> writer.i24(0xD5010F)
		XTEST       -> writer.i24(0xD6010F)
		ENCLU       -> writer.i24(0xD7010F)
		RDPKRU      -> writer.i24(0xEE010F)
		WRPKRU      -> writer.i24(0xEF010F)
		SWAPGS      -> writer.i24(0xF8010F)
		RDTSCP      -> writer.i24(0xF9010F)
		UIRET       -> writer.i32(0xEC010FF3)
		TESTUI      -> writer.i32(0xED010FF3)
		CLUI        -> writer.i32(0xEE010FF3)
		STUI        -> writer.i32(0xEF010FF3)
		SYSCALL     -> writer.i16(0x050F)
		CLTS        -> writer.i16(0x060F)
		SYSRET      -> writer.i16(0x070F)
		SYSRETQ     -> writer.i24(0x070F48)
		INVD        -> writer.i16(0x080F)
		WBINVD      -> writer.i16(0x090F)
		WBNOINVD    -> writer.i24(0x090FF3)
		ENDBR32     -> writer.i32(0xFB1E0FF3)
		ENDBR64     -> writer.i32(0xFA1E0FF3)
		WRMSR       -> writer.i16(0x300F)
		WRMSRLIST   -> writer.i32(0xC6010FF3)
		RDTSC       -> writer.i16(0x310F)
		RDMSR       -> writer.i16(0x320F)
		RDMSRLIST   -> writer.i32(0xC6010FF2)
		RDPMC       -> writer.i16(0x330F)
		SYSENTER    -> writer.i16(0x340F)
		SYSEXIT     -> writer.i16(0x350F)
		SYSEXITQ    -> writer.i24(0x350F48)
		GETSEC      -> writer.i16(0x370F)
		EMMS        -> writer.i16(0x770F)
		CPUID       -> writer.i16(0xA20F)
		RSM         -> writer.i16(0xAA0F)
		LFENCE      -> writer.i24(0xE8AE0F)
		MFENCE      -> writer.i24(0xF0AE0F)
		SFENCE      -> writer.i24(0xF8AE0F)
		SERIALIZE   -> writer.i24(0xE8010F)
		XSUSLDTRK   -> writer.i32(0xE8010FF2)
		XRESLDTRK   -> writer.i32(0xE9010FF2)
		SETSSBSY    -> writer.i32(0xE8010FF3)
		SAVEPREVSSP -> writer.i32(0xEA010FF3)
		UD1         -> writer.i16(0xB90F)
		UD2         -> writer.i16(0x0B0F)
		VZEROUPPER  -> writer.i24(0x77F8C5)
		VZEROALL    -> writer.i24(0x77FCC5)
		TILERELEASE -> writer.i40(0xC04978E2C4)
		RETURN      -> encodeRETURN()
		else        -> invalid()
	}}



	private fun assemble1(node: InsNode, op1: OpNode) { when(node.mnemonic) {
		PUSH -> encodePUSH(op1)
		POP  -> encodePOP(op1)
		
		PUSHW -> when(op1.asReg) {
			Reg.FS -> writer.i24(0xA80F66)
			Reg.GS -> writer.i32(0xA80F66)
			else   -> invalid()
		}
		
		POPW -> when(op1.asReg) {
			Reg.FS -> writer.i24(0xA10F66)
			Reg.GS -> writer.i32(0xA10F66)
			else   -> invalid()
		}
		
		NOT   -> Enc { 0xF6 + EXT2 + R1111 }.encode1RM(op1, 0)
		NEG   -> Enc { 0xF6 + EXT3 + R1111 }.encode1RM(op1, 0)
		MUL   -> Enc { 0xF6 + EXT4 + R1111 }.encode1RM(op1, 0)
		IMUL  -> Enc { 0xF6 + EXT5 + R1111 }.encode1RM(op1, 0)
		DIV   -> Enc { 0xF6 + EXT6 + R1111 }.encode1RM(op1, 0)
		IDIV  -> Enc { 0xF6 + EXT7 + R1111 }.encode1RM(op1, 0)
		INC   -> Enc { 0xFE + EXT0 + R1111 }.encode1RM(op1, 0)
		DEC   -> Enc { 0xFE + EXT1 + R1111 }.encode1RM(op1, 0)
		NOP   -> Enc { E0F+0x1F+R1110 }.encode1RM(op1, 0)
		RET   -> byte(0xC2).imm(op1.asImm, WORD)
		RETF  -> byte(0xCA).imm(op1.asImm, WORD)
		RETW  -> word(0xC266).imm(op1.asImm, WORD)
		RETFQ -> word(0xCA48).imm(op1.asImm, WORD)
		INT   -> byte(0xCD).imm(op1.asImm, BYTE)

		LOOP           -> encode1Rel(0xE2, BYTE, op1.asImm)
		LOOPE, LOOPZ   -> encode1Rel(0xE1, BYTE, op1.asImm)
		LOOPNE, LOOPNZ -> encode1Rel(0xE0, BYTE, op1.asImm)

		CALL    -> encodeCALL(op1)
		JMP     -> encodeJMP(op1)
		JMPF    -> Enc { 0xFF + R1110 + EXT5 }.encode1M(op1, 0)
		CALLF   -> Enc { 0xFF + R1110 + EXT3 }.encode1M(op1, 0)
		JECXZ   -> { byte(0x67); encode1Rel(0xE3, BYTE, op1.asImm) }
		JRCXZ   -> encode1Rel(0xE3, BYTE, op1)
		DLLCALL -> encodeDLLCALL(op1.asImm)

		JO            -> encode1Rel832(0x70, 0x800F, op1.asImm)
		JNO           -> encode1Rel832(0x71, 0x810F, op1.asImm)
		JB, JNAE, JC  -> encode1Rel832(0x72, 0x820F, op1.asImm)
		JNB, JAE, JNC -> encode1Rel832(0x73, 0x830F, op1.asImm)
		JZ, JE        -> encode1Rel832(0x74, 0x840F, op1.asImm)
		JNZ, JNE      -> encode1Rel832(0x75, 0x850F, op1.asImm)
		JBE, JNA      -> encode1Rel832(0x76, 0x860F, op1.asImm)
		JNBE, JA      -> encode1Rel832(0x77, 0x870F, op1.asImm)
		JS            -> encode1Rel832(0x78, 0x880F, op1.asImm)
		JNS           -> encode1Rel832(0x79, 0x890F, op1.asImm)
		JP, JPE       -> encode1Rel832(0x7A, 0x8A0F, op1.asImm)
		JNP, JPO      -> encode1Rel832(0x7B, 0x8B0F, op1.asImm)
		JL, JNGE      -> encode1Rel832(0x7C, 0x8C0F, op1.asImm)
		JNL, JGE      -> encode1Rel832(0x7D, 0x8D0F, op1.asImm)
		JLE, JNG      -> encode1Rel832(0x7E, 0x8E0F, op1.asImm)
		JNLE, JG      -> encode1Rel832(0x7F, 0x8F0F, op1.asImm)

		SETO                -> Enc { E0F + 0x90 + R0001 }.encode1RM(op1, 0)
		SETNO               -> Enc { E0F + 0x91 + R0001 }.encode1RM(op1, 0)
		SETB, SETNAE, SETC  -> Enc { E0F + 0x92 + R0001 }.encode1RM(op1, 0)
		SETNB, SETAE, SETNC -> Enc { E0F + 0x93 + R0001 }.encode1RM(op1, 0)
		SETZ, SETE          -> Enc { E0F + 0x94 + R0001 }.encode1RM(op1, 0)
		SETNZ, SETNE        -> Enc { E0F + 0x95 + R0001 }.encode1RM(op1, 0)
		SETBE, SETNA        -> Enc { E0F + 0x96 + R0001 }.encode1RM(op1, 0)
		SETNBE, SETA        -> Enc { E0F + 0x97 + R0001 }.encode1RM(op1, 0)
		SETS                -> Enc { E0F + 0x98 + R0001 }.encode1RM(op1, 0)
		SETNS               -> Enc { E0F + 0x99 + R0001 }.encode1RM(op1, 0)
		SETP, SETPE         -> Enc { E0F + 0x9A + R0001 }.encode1RM(op1, 0)
		SETNP, SETPO        -> Enc { E0F + 0x9B + R0001 }.encode1RM(op1, 0)
		SETL, SETNGE        -> Enc { E0F + 0x9C + R0001 }.encode1RM(op1, 0)
		SETNL, SETGE        -> Enc { E0F + 0x9D + R0001 }.encode1RM(op1, 0)
		SETLE, SETNG        -> Enc { E0F + 0x9E + R0001 }.encode1RM(op1, 0)
		SETNLE, SETG        -> Enc { E0F + 0x9F + R0001 }.encode1RM(op1, 0)

		FLDENV   -> Enc { 0xD9 + EXT4 }.encode1MEM(op1)
		FLDCW    -> Enc { 0xD9 + EXT5 }.encode1M16(op1)
		FNSTENV  -> Enc { 0xD9 + EXT6 }.encode1MEM(op1)
		FNSTCW   -> Enc { 0xD9 + EXT7 }.encode1M16(op1)
		FSTENV   -> Enc { P9B + 0xD9 + EXT6 }.encode1MEM(op1)
		FSTCW    -> Enc { P9B + 0xD9 + EXT7 }.encode1M16(op1)
		FRSTOR   -> Enc { 0xDD + EXT4 }.encode1MEM(op1)
		FSAVE    -> Enc { P9B + 0xDD + EXT6 }.encode1MEM(op1)
		FNSAVE   -> Enc { 0xDD + EXT6 }.encode1MEM(op1)
		FBSTP    -> Enc { 0xDF + EXT6 }.encode1MSingle(op1, TWORD)
		FBLD     -> Enc { 0xDF + EXT4 }.encode1MSingle(op1, TWORD)
		FUCOM    -> encode1ST(0xE0DD, op1.asST)
		FUCOMP   -> encode1ST(0xE8DD, op1.asST)
		FFREE    -> encode1ST(0xC0DD, op1.asST)
		FXCH     -> encode1ST(0xC8D9, op1.asST)
		FADDP    -> encode1ST(0xC0DE, op1.asST)
		FMULP    -> encode1ST(0xC8DE, op1.asST)
		FSUBP    -> encode1ST(0xE8DE, op1.asST)
		FSUBRP   -> encode1ST(0xE0DE, op1.asST)
		FDIVP    -> encode1ST(0xF8DE, op1.asST)
		FDIVRP   -> encode1ST(0xF0DE, op1.asST)
		FADD     -> encodeFADD1(0xC0D8, 0, op1)
		FMUL     -> encodeFADD1(0xC8D8, 1, op1)
		FCOM     -> encodeFADD1(0xD0D8, 2, op1)
		FCOMP    -> encodeFADD1(0xD8D8, 3, op1)
		FSUB     -> encodeFADD1(0xE0D8, 4, op1)
		FSUBR    -> encodeFADD1(0xE8D8, 5, op1)
		FDIV     -> encodeFADD1(0xF0D8, 6, op1)
		FDIVR    -> encodeFADD1(0xF8D8, 7, op1)
		FIADD    -> encodeFIADD(0, op1.asMem)
		FIMUL    -> encodeFIADD(1, op1.asMem)
		FICOM    -> encodeFIADD(2, op1.asMem)
		FICOMP   -> encodeFIADD(3, op1.asMem)
		FISUB    -> encodeFIADD(4, op1.asMem)
		FISUBR   -> encodeFIADD(5, op1.asMem)
		FIDIV    -> encodeFIADD(6, op1.asMem)
		FIDIVR   -> encodeFIADD(7, op1.asMem)
		FCMOVB   -> encode1ST(0xC0DA, op1.asST)
		FCMOVE   -> encode1ST(0xC8DA, op1.asST)
		FCMOVBE  -> encode1ST(0xD0DA, op1.asST)
		FCMOVU   -> encode1ST(0xD8DA, op1.asST)
		FCMOVNB  -> encode1ST(0xC0DB, op1.asST)
		FCMOVNE  -> encode1ST(0xC8DB, op1.asST)
		FCMOVNBE -> encode1ST(0xD0DB, op1.asST)
		FCMOVNU  -> encode1ST(0xD8DB, op1.asST)
		FCOMI    -> encode1ST(0xF0DB, op1.asST)
		FCOMIP   -> encode1ST(0xF0DF, op1.asST)
		FUCOMI   -> encode1ST(0xE8DB, op1.asST)
		FUCOMIP  -> encode1ST(0xE8DF, op1.asST)
		
		FSTSW -> when {
			op1.reg == Reg.AX -> writer.i24(0xE0DF9B)
			op1.isMem -> Enc { P9B + 0xDD + EXT7 }.encode1MSingle(op1, WORD)
			else -> invalid()
		}
		
		FNSTSW -> when {
			op1.reg == Reg.AX -> writer.i16(0xE0DF)
			op1.isMem -> Enc { 0xDD + EXT7 }.encode1MSingle(op1, WORD)
			else -> invalid()
		}
		
		FST -> when(op1.type) {
			REG -> word(0xD0DD + (op1.asST.value shl 8))
			MEM -> when(op1.width) {
				DWORD  -> encode1MAny(0xD9, 2, op1)
				QWORD  -> encode1MAny(0xDD, 2, op1)
				else   -> invalid()
			}
			else -> invalid()
		}
		
		FSTP -> when(op1.type) {
			REG -> word(0xD8DD + (op1.asST.value shl 8))
			MEM -> when(op1.width) {
				DWORD  -> encode1MAny(0xD9, 3, op1)
				QWORD  -> encode1MAny(0xDD, 3, op1)
				TWORD  -> encode1MAny(0xDB, 7, op1)
				else   -> invalid()
			}
			else -> invalid()
		}
		
		FILD -> when(op1.asMem.width) {
			WORD  -> encode1MAny(0xDF, 0, op1)
			DWORD -> encode1MAny(0xDB, 0, op1)
			QWORD -> encode1MAny(0xDF, 5, op1)
			else  -> invalid()
		}
		
		FISTTP -> when(op1.asMem.width) {
			WORD  -> encode1MAny(0xDF, 1, op1)
			DWORD -> encode1MAny(0xDB, 1, op1)
			QWORD -> encode1MAny(0xDD, 1, op1)
			else  -> invalid()
		}
		
		FIST -> when(op1.asMem.width) {
			WORD  -> encode1MAny(0xDF, 2, op1)
			DWORD -> encode1MAny(0xDB, 2, op1)
			else  -> invalid()
		}
		
		FISTP -> when(op1.asMem.width) {
			WORD  -> encode1MAny(0xDF, 3, op1)
			DWORD -> encode1MAny(0xDB, 3, op1)
			QWORD -> encode1MAny(0xDF, 7, op1)
			else  -> invalid()
		}
		
		FLD -> when(op1.type) {
			MEM -> when(op1.width) {
				DWORD -> encode1MAny(0xD9, 0, op1)
				QWORD -> encode1MAny(0xDD, 0, op1)
				TWORD -> encode1MAny(0xDB, 5, op1)
				else -> invalid()
			}
			REG -> word(0xC0D9 + (op1.asST.value shl 8))
			else -> invalid()
		}

		SLDT     -> Enc { E0F + 0x00 + EXT0 + R1110 }.encodeSLDT(op1)
		STR      -> Enc { E0F + 0x00 + EXT1 + R1110 }.encodeSLDT(op1)
		LLDT     -> Enc { E0F + 0x00 + EXT2 + R0010 }.encode1RM(op1, 0)
		LTR      -> Enc { E0F + 0x00 + EXT3 + R0010 }.encode1RM(op1, 0)
		VERR     -> Enc { E0F + 0x00 + EXT4 + R0010 }.encode1RM(op1, 0)
		VERW     -> Enc { E0F + 0x00 + EXT5 + R0010 }.encode1RM(op1, 0)
		SGDT     -> Enc { E0F + 0x01 + EXT0 }.encode1MEM(op1)
		SIDT     -> Enc { E0F + 0x01 + EXT1 }.encode1MEM(op1)
		LGDT     -> Enc { E0F + 0x01 + EXT2 }.encode1MEM(op1)
		LIDT     -> Enc { E0F + 0x01 + EXT3 }.encode1MEM(op1)
		SMSW     -> Enc { E0F + 0x01 + EXT4 + R1110 }.encodeSLDT(op1)
		LMSW     -> Enc { E0F + 0x01 + EXT6 + R0010 }.encode1RM(op1, 0)
		INVLPG   -> Enc { E0F + 0x01 + EXT7 }.encode1MEM(op1)
		RSTORSSP -> Enc { PF3 + E0F + 0x01 + EXT5 }.encode1M64(op1)

		PREFETCHW   -> Enc { E0F + 0x0D + EXT1 }.encode1M8(op1)
		PREFETCHWT1 -> Enc { E0F + 0x0D + EXT2 }.encode1M8(op1)
		PREFETCHNTA -> Enc { E0F + 0x18 + EXT0 }.encode1M8(op1)
		PREFETCHT0  -> Enc { E0F + 0x18 + EXT1 }.encode1M8(op1)
		PREFETCHT1  -> Enc { E0F + 0x18 + EXT2 }.encode1M8(op1)
		PREFETCHT2  -> Enc { E0F + 0x18 + EXT3 }.encode1M8(op1)

		CLDEMOTE -> Enc { E0F + 0x1C + EXT0 }.encode1M8(op1)

		RDSSPD -> Enc { PF3 + E0F + 0x1E + EXT1 + R0100 }.encode1R(op1.asReg)
		RDSSPQ -> Enc { PF3 + E0F + 0x1E + EXT1 + R1000 + RW}.encode1R(op1.asReg)

		FXSAVE     -> Enc { E0F + 0xAE + EXT0 }.encode1MEM(op1)
		FXSAVE64   -> Enc { E0F + 0xAE + EXT0 + RW }.encode1MEM(op1)
		FXRSTOR    -> Enc { E0F + 0xAE + EXT1 }.encode1MEM(op1)
		FXRSTOR64  -> Enc { E0F + 0xAE + EXT1 + RW }.encode1MEM(op1)
		LDMXCSR    -> Enc { E0F + 0xAE + EXT2 }.encode1M32(op1)
		STMXCSR    -> Enc { E0F + 0xAE + EXT3 }.encode1M32(op1)
		XSAVE      -> Enc { E0F + 0xAE + EXT4 }.encode1MEM(op1)
		XSAVE64    -> Enc { E0F + 0xAE + EXT4 + RW }.encode1MEM(op1)
		XRSTOR     -> Enc { E0F + 0xAE + EXT5 }.encode1MEM(op1)
		XRSTOR64   -> Enc { E0F + 0xAE + EXT5 + RW }.encode1MEM(op1)
		XSAVEOPT   -> Enc { E0F + 0xAE + EXT6 }.encode1MEM(op1)
		XSAVEOPT64 -> Enc { E0F + 0xAE + EXT6 + RW }.encode1MEM(op1)
		CLFLUSH    -> Enc { E0F + 0xAE + EXT7 }.encode1M8(op1)
		RDFSBASE   -> Enc { PF3 + E0F + 0xAE + EXT0 + R1100 }.encode1R(op1.asReg)
		RDGSBASE   -> Enc { PF3 + E0F + 0xAE + EXT1 + R1100 }.encode1R(op1.asReg)
		WRFSBASE   -> Enc { PF3 + E0F + 0xAE + EXT2 + R1100 }.encode1R(op1.asReg)
		WRGSBASE   -> Enc { PF3 + E0F + 0xAE + EXT3 + R1100 }.encode1R(op1.asReg)
		PTWRITE    -> Enc { PF3 + E0F + 0xAE + EXT4 + R1100 }.encode1RM(op1, 0)
		INCSSPD    -> Enc { PF3 + E0F + 0xAE + EXT5 + R0100 }.encode1R(op1.asReg)
		INCSSPQ    -> Enc { PF3 + E0F + 0xAE + EXT5 + R1000 + RW }.encode1R(op1.asReg)
		CLRSSBSY   -> Enc { PF3 + E0F + 0xAE + EXT6 }.encode1M64(op1)
		UMWAIT     -> Enc { PF2 + E0F + 0xAE + EXT6 + R0100 }.encode1R(op1.asReg)
		CLWB       -> Enc { P66 + E0F + 0xAE + EXT6 }.encode1M8(op1)
		TPAUSE     -> Enc { P66 + E0F + 0xAE + EXT6 + R0100 }.encode1R(op1.asReg)
		CLFLUSHOPT -> Enc { P66 + E0F + 0xAE + EXT7 }.encode1M8(op1)

		CMPXCHG8B  -> Enc { E0F+0xC7+EXT1 }.encode1M64(op1)
		CMPXCHG16B -> Enc { E0F+0xC7+EXT1+RW }.encode1MSingle(op1, XWORD)
		XRSTORS    -> Enc { E0F+0xC7+EXT3 }.encode1MEM(op1)
		XRSTORS64  -> Enc { E0F+0xC7+EXT3+RW }.encode1MEM(op1)
		XSAVEC     -> Enc { E0F+0xC7+EXT4 }.encode1MEM(op1)
		XSAVEC64   -> Enc { E0F+0xC7+EXT4+RW }.encode1MEM(op1)
		XSAVES     -> Enc { E0F+0xC7+EXT5 }.encode1MEM(op1)
		XSAVES64   -> Enc { E0F+0xC7+EXT5+RW }.encode1MEM(op1)
		VMPTRLD    -> Enc { E0F+0xC7+EXT6 }.encode1M64(op1)
		VMPTRST    -> Enc { E0F+0xC7+EXT7 }.encode1M64(op1)
		RDRAND     -> Enc { E0F+0xC7+EXT6+R1110 }.encode1R(op1.asReg)
		RDSEED     -> Enc { E0F+0xC7+EXT7+R1110 }.encode1R(op1.asReg)
		VMXON      -> Enc { PF3+E0F+0xC7+EXT6 }.encode1M64(op1)
		VMCLEAR    -> Enc { P66+E0F+0xC7+EXT6 }.encode1M64(op1)
		SENDUIPI   -> Enc { PF3+E0F+0xC7+EXT6+R1000 }.encode1R(op1.asReg)
		RDPID      -> Enc { PF3+E0F+0xC7+EXT7+R1000 }.encode1R(op1.asReg)
		BSWAP      -> Enc { E0F+0xC8+R1100 }.encode1O(op1.asReg)

		UMONITOR -> Enc { PF3+E0F+0xAE+EXT6 }.encode1RA(op1.reg)

		XABORT -> dword(0xF8C6).imm(op1.asImm, BYTE)
		XBEGIN -> dword(0xF8C7).rel(op1.asImm, DWORD)

		HRESET -> writer.i40(0xC0F03A0FF3L).imm(op1.asImm, BYTE)

		else -> invalid()
	}}
	

	
	private fun assemble2(node: InsNode, op1: OpNode, op2: OpNode) { when(node.mnemonic) {
		IMUL -> Enc { E0F + 0xAF + R1110 }.encode2RRM(op1.asReg, op2, 0)

		MOVSXD -> when(op2.width) {
			DWORD -> Enc { 0x63 + R1100 + MM }.encode2RRM(op1.reg, op2, 0)
			else  -> invalid()
		}

		MOVZX -> encodeMOVZX(0xB6, op1, op2)
		MOVSX -> encodeMOVZX(0xBE, op1, op2)

		MOV    -> encodeMOV(op1, op2)
		IN     -> encodeIN(op1.asReg, op2)
		OUT    -> encodeOUT(op1, op2.asReg)

		XCHG -> when {
			op1.isMem         -> Enc { 0x86 + R1111 }.encode2RM(op2.asReg, op1, 0)
			op2.isMem         -> Enc { 0x86 + R1111 }.encode2RM(op1.asReg, op2, 0)
			op1.width == BYTE -> Enc { 0x86 + R1111 }.encode2RR(op1.reg, op2.reg)
			op1.reg.isA       -> Enc { 0x90 + R1110 }.encode1O(op2.reg)
			op2.reg.isA       -> Enc { 0x90 + R1110 }.encode1O(op1.reg)
			else              -> Enc { 0x86 + R1111 }.encode2RR(op1.reg, op2.reg)
		}

		TEST -> encodeTEST(op1, op2)

		LEA -> Enc { 0x8D + R1110 }.encode2RM(op1.asReg, op2.asMem, 0)

		ADD -> encodeADD(0x00, Enc.EXT0, op1, op2)
		OR  -> encodeADD(0x08, Enc.EXT1, op1, op2)
		ADC -> encodeADD(0x10, Enc.EXT2, op1, op2)
		SBB -> encodeADD(0x18, Enc.EXT3, op1, op2)
		AND -> encodeADD(0x20, Enc.EXT4, op1, op2)
		SUB -> encodeADD(0x28, Enc.EXT5, op1, op2)
		XOR -> encodeADD(0x30, Enc.EXT6, op1, op2)
		CMP -> encodeADD(0x38, Enc.EXT7, op1, op2)

		ROL -> encodeROL(Enc.EXT0, op1, op2)
		ROR -> encodeROL(Enc.EXT1, op1, op2)
		RCL -> encodeROL(Enc.EXT2, op1, op2)
		RCR -> encodeROL(Enc.EXT3, op1, op2)
		SAL -> encodeROL(Enc.EXT4, op1, op2)
		SHL -> encodeROL(Enc.EXT4, op1, op2)
		SHR -> encodeROL(Enc.EXT5, op1, op2)
		SAR -> encodeROL(Enc.EXT7, op1, op2)

		CMOVO                  -> Enc { E0F + 0x40 + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVNO                 -> Enc { E0F + 0x41 + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVB, CMOVNAE, CMOVC  -> Enc { E0F + 0x42 + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVNB, CMOVAE, CMOVNC -> Enc { E0F + 0x43 + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVZ, CMOVE           -> Enc { E0F + 0x44 + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVNZ, CMOVNE         -> Enc { E0F + 0x45 + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVBE, CMOVNA         -> Enc { E0F + 0x46 + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVNBE, CMOVA         -> Enc { E0F + 0x47 + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVS                  -> Enc { E0F + 0x48 + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVNS                 -> Enc { E0F + 0x49 + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVP, CMOVPE          -> Enc { E0F + 0x4A + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVNP, CMOVPO         -> Enc { E0F + 0x4B + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVL, CMOVNGE         -> Enc { E0F + 0x4C + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVNL, CMOVGE         -> Enc { E0F + 0x4D + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVLE, CMOVNG         -> Enc { E0F + 0x4E + R1110 }.encode2RRM(op1.asReg, op2, 0)
		CMOVNLE, CMOVG         -> Enc { E0F + 0x4F + R1110 }.encode2RRM(op1.asReg, op2, 0)

		FADD     -> encode2STST(0xC0D8, 0xC0DC, op1, op2)
		FMUL     -> encode2STST(0xC8D8, 0xC8DC, op1, op2)
		FSUB     -> encode2STST(0xE0D8, 0xE8DC, op1, op2)
		FSUBR    -> encode2STST(0xE8D8, 0xE0DC, op1, op2)
		FDIV     -> encode2STST(0xF0D8, 0xF8DC, op1, op2)
		FDIVR    -> encode2STST(0xF8D8, 0xF0DC, op1, op2)
		FXCH     -> encode2STST(0xC8D9, 0xC8D9, op1, op2)
		FADDP    -> encode2STIST0(0xC0DE, op1, op2)
		FMULP    -> encode2STIST0(0xC8DE, op1, op2)
		FSUBP    -> encode2STIST0(0xE8DE, op1, op2)
		FSUBRP   -> encode2STIST0(0xE0DE, op1, op2)
		FDIVP    -> encode2STIST0(0xF8DE, op1, op2)
		FDIVRP   -> encode2STIST0(0xF0DE, op1, op2)
		FCMOVB   -> encodeFCMOVCC(0xC0DA, op1, op2)
		FCMOVE   -> encodeFCMOVCC(0xC8DA, op1, op2)
		FCMOVBE  -> encodeFCMOVCC(0xD0DA, op1, op2)
		FCMOVU   -> encodeFCMOVCC(0xD8DA, op1, op2)
		FCMOVNB  -> encodeFCMOVCC(0xC0DB, op1, op2)
		FCMOVNE  -> encodeFCMOVCC(0xC8DB, op1, op2)
		FCMOVNBE -> encodeFCMOVCC(0xD0DB, op1, op2)
		FCMOVNU  -> encodeFCMOVCC(0xD8DB, op1, op2)
		FCOM     -> encode2ST0STI(0xD0D8, op1, op2)
		FCOMI    -> encode2ST0STI(0xF0DB, op1, op2)
		FCOMIP   -> encode2ST0STI(0xF0DF, op1, op2)
		FCOMP    -> encode2ST0STI(0xD8D8, op1, op2)
		FUCOM    -> encode2ST0STI(0xE0DD, op1, op2)
		FUCOMI   -> encode2ST0STI(0xE8DB, op1, op2)
		FUCOMP   -> encode2ST0STI(0xE8DD, op1, op2)
		FUCOMIP  -> encode2ST0STI(0xE8DF, op1, op2)

		LAR -> encodeLAR(0x020F, op1.asReg, op2)
		LSL -> encodeLAR(0x030F, op1.asReg, op2)
		UD0 -> Enc { E0F+0xFF+R1110 }.encode2RRM(op1.asReg, op2, 0)
		UD1 -> Enc { E0F+0xB9+R1110 }.encode2RRM(op1.asReg, op2, 0)

		VMREAD -> Enc { E0F+0x78+R1000 }.encode2RMR(op1, op2.asReg, 0)
		VMWRITE -> Enc { E0F+0x79+R1000 }.encode2RRM(op1.asReg, op2, 0)

		BT  -> encodeBT(0xA3, Enc.EXT4, op1, op2)
		BTS -> encodeBT(0xAB, Enc.EXT5, op1, op2)
		BTR -> encodeBT(0xB3, Enc.EXT6, op1, op2)
		BTC -> encodeBT(0xBB, Enc.EXT7, op1, op2)

		CMPXCHG -> Enc { E0F+0xB0+R1111 }.encode2RMR(op1, op2.asReg, 0)
		LSS     -> Enc { E0F+0xB2+R1110 }.encode2RMEM(op1.asReg, op2.asMem)
		LFS     -> Enc { E0F+0xB4+R1110 }.encode2RMEM(op1.asReg, op2.asMem)
		LGS     -> Enc { E0F+0xB5+R1110 }.encode2RMEM(op1.asReg, op2.asMem)
		POPCNT  -> Enc { PF3+E0F+0xB8+R1110 }.encode2RRM(op1.asReg, op2, 0)
		BSF     -> Enc { E0F+0xBC+R1110 }.encode2RRM(op1.asReg, op2, 0)
		BSR     -> Enc { E0F+0xBD+R1110 }.encode2RRM(op1.asReg, op2, 0)
		TZCNT   -> Enc { PF3+E0F+0xBC+R1110 }.encode2RRM(op1.asReg, op2, 0)
		LZCNT   -> Enc { PF3+E0F+0xBD+R1110 }.encode2RRM(op1.asReg, op2, 0)
		XADD    -> Enc { E0F+0xC0+R1111 }.encode2RMR(op1, op2.asReg, 0)
		MOVNTI  -> Enc { E0F+0xC3+R1100 }.encode2RM(op2.asReg, op1.asMem, 0)

		INVEPT  -> encodeINVEPT(0x80, op1, op2)
		INVVPID -> encodeINVEPT(0x81, op1, op2)
		INVPCID -> encodeINVEPT(0x82, op1, op2)

		ADCX -> Enc { P66+E38+0xF6+R1100 }.encode2RRM(op1.asReg, op2, 0)
		ADOX -> Enc { PF3+E38+0xF6+R1100 }.encode2RRM(op1.asReg, op2, 0)

		MOVDIRI -> Enc { E38+0xF9+R1100 }.encode2RM(op2.asReg, op1.asMem, 0)
		AADD -> Enc { PNP+E38+0xFC+R1100 }.encode2RM(op2.asReg, op1.asMem, 0)
		AAND -> Enc { P66+E38+0xFC+R1100 }.encode2RM(op2.asReg, op1.asMem, 0)
		AOR  -> Enc { PF2+E38+0xFC+R1100 }.encode2RM(op2.asReg, op1.asMem, 0)
		AXOR -> Enc { PF3+E38+0xFC+R1100 }.encode2RM(op2.asReg, op1.asMem, 0)

		WRSSD  -> Enc { PNP+E38+0xF6+R0100 }.encode2RM(op2.asReg, op1.asMem, 0)
		WRSSQ  -> Enc { PNP+E38+0xF6+R1000+RW }.encode2RM(op2.asReg, op1.asMem, 0)
		WRUSSD -> Enc { P66+E38+0xF5+R0100 }.encode2RM(op2.asReg, op1.asMem, 0)
		WRUSSQ -> Enc { P66+E38+0xF5+R1000+RW }.encode2RM(op2.asReg, op1.asMem, 0)

		MOVBE -> when(op1.type) {
			REG  -> Enc { E38+0xF0+R1110 }.encode2RM(op1.reg, op2.asMem, 0)
			MEM  -> Enc { E38+0xF1+R1110 }.encode2RM(op2.reg, op1.asMem, 0)
			else -> invalid()
		}

		ENQCMD    -> Enc { PF2+E38+0xF8 }.encode2RAM512(op1.reg, op2.asMem)
		ENQCMDS   -> Enc { PF3+E38+0xF8 }.encode2RAM512(op1.reg, op2.asMem)
		MOVDIR64B -> Enc { P66+E38+0xF8 }.encode2RAM512(op1.reg, op2.asMem)

		CRC32 -> when(op2.width) {
			BYTE -> Enc { PF2+E38+0xF0+R1100+MM }.encode2RRM(op1.asReg, op2, 0)
			WORD -> { byte(0x66); Enc { PF2+E38+0xF1+R0100+MM }.encode2RRM(op1.asReg, op2, 0) }
			else -> Enc { PF2+E38+0xF1+R1100 }.encode2RRM(op1.asReg, op2, 0)
		}

		else -> invalid()
	}}



	private fun assemble3(node: InsNode, op1: OpNode, op2: OpNode, op3: OpNode) { when(node.mnemonic) {
		IMUL -> encodeIMUL(op1.asReg, op2, op3)
		SHLD -> encodeSHLD(0xA4, op1, op2.asReg, op3)
		SHRD -> encodeSHLD(0xAC, op1, op2.asReg, op3)
		else -> invalid()
	}}



	/*
	Misc. encodings
	 */



	/**
	 *     0F BE  MOVSX   R_RM8   1110
	 *     0F BF  MOVSX   R_RM16  1100
	 *     0F B6  MOVZX   R_RM8   1110
	 *     0F B7  MOVZX   R_RM16  1100
	 */
	private fun encodeMOVZX(opcode: Int, op1: OpNode, op2: OpNode) {
		when(op2.width) {
			BYTE -> Enc { E0F+opcode+R1110+MM }.encode2RRM(op1.asReg, op2, 0)
			WORD -> Enc { E0F+opcode+1+R1100+MM }.encode2RRM(op1.asReg, op2, 0)
			else -> invalid()
		}
	}

	/**
	 *     66 0F 38 80  INVEPT   R_M128  1000
	 *     66 0F 38 81  INVVPID  R_M128  1000
	 *     66 0F 38 82  INVPCID  R_M128  1000
	 */
	private fun encodeINVEPT(opcode: Int, op1: OpNode, op2: OpNode) {
		when {
			op2.width != null && op2.width != XWORD -> invalid()
			else -> Enc { P66+E38+opcode+R1000+MM }.encode2RM(op1.asReg, op2.asMem, 0)
		}
	}

	/**
	 *     0F A3    BT   RM_R   1110
	 *     0F BA/4  BT   RM_I8  1110
	 *     0F AB    BTS  RM_R   1110
	 *     0F BA/5  BTS  RM_I8  1110
	 *     0F B3    BTR  RM_R   1110
	 *     0F BA/6  BTR  RM_I8  1110
	 *     0F BB    BTC  RM_R   1110
	 *     0F BA/7  BTC  RM_I8  1110
	 */
	private fun encodeBT(opcode: Int, ext: Int, op1: OpNode, op2: OpNode) {
		when(op2.type) {
			REG  -> Enc { E0F+opcode+R1110 }.encode2RMR(op1, op2.reg, 0)
			IMM  -> Enc { E0F+0xBA+R1110+ext }.encode1RM(op1, 1).imm(op2, BYTE)
			else -> invalid()
		}
	}

	private fun encodeLAR(opcode: Int, op1: Reg, op2: OpNode) {
		when(op2.type) {
			REG -> {
				if(op1 !in OpMask(0b1110) || op2.reg !in OpMask(0b1110)) invalid()
				writeRex(0, op2.reg.rex, 0, op1.rex)
				word(opcode)
				writeModRM(0b11, op2.reg.value, op1.value)
			}
			MEM -> {
				if(op1 !in OpMask(0b1110) || (op2.width != null && op2.width != WORD)) invalid()
				val mem = resolveMem(op2)
				writeA32(mem)
				writeRex(0, op1.rex, mem.rexX, mem.rexB)
				word(opcode)
				mem.write(op1.value, 0)
			}
			else -> invalid()
		}
	}

	private fun Enc.encodeSLDT(op1: OpNode) {
		when(op1.type) {
			REG -> encode1R(op1.reg)
			MEM -> encode1M16(op1)
			IMM -> invalid()
		}
	}

	private fun encodeIN(op1: Reg, op2: OpNode) {
		when {
			!op1.isA -> invalid()
			op2.reg == Reg.DX -> when(op1.width) {
				BYTE  -> byte(0xEC)
				WORD  -> word(0xED66)
				DWORD -> byte(0xED)
				else  -> invalid()
			}
			op2.isImm -> when(op1.width) {
				BYTE  -> byte(0xE4).imm(op2, BYTE)
				WORD  -> word(0xE566).imm(op2, BYTE)
				DWORD -> byte(0xE5).imm(op2, BYTE)
				else  -> invalid()
			}
			else -> invalid()
		}
	}

	private fun encodeOUT(op1: OpNode, op2: Reg) {
		when {
			!op2.isA -> invalid()
			op1.reg == Reg.DX -> when(op2.width) {
				BYTE  -> byte(0xEE)
				WORD  -> word(0xEF66)
				DWORD -> byte(0xEF)
				else  -> invalid()
			}
			op1.isImm -> when(op2.width) {
				BYTE  -> byte(0xE6).imm(op1, BYTE)
				WORD  -> word(0xE766).imm(op1, BYTE)
				DWORD -> byte(0xE7).imm(op1, BYTE)
				else  -> invalid()
			}
			else -> invalid()
		}
	}

	/**
	 *     0F A4  SHLD  RM_R_I8  1110
	 *     0F A5  SHLD  RM_R_CL  1110
	 *     0F AC  SHRD  RM_R_I8  1110
	 *     0F AD  SHRD  RM_R_CL  1110
	 */
	private fun encodeSHLD(opcode: Int, op1: OpNode, op2: Reg, op3: OpNode) {
		when {
			op3.reg == Reg.CL -> Enc { E0F+opcode+1+R1110 }.encode2RMR(op1, op2, 0)
			op3.type == IMM -> Enc { E0F+opcode+R1110 }.encode2RMR(op1, op2, 1).imm(op3, BYTE)
			else -> invalid()
		}
	}

	/**
	 *     6B    IMUL  R_RM_I8  0111
	 *     69    IMUL  R_RM_I   0111
	 */
	private fun encodeIMUL(op1: Reg, op2: OpNode, op3: OpNode) {
		val imm = resolveImm(op3)

		if(op3.width == BYTE || (op3.width == null && !hasImmReloc && imm.isImm8)) {
			Enc { 0x6B + R1110 }.encode2RRM(op1, op2, 1).imm(op3, BYTE, imm)
		} else {
			val width = op1.immWidth()
			Enc { 0x69 + R1110 }.encode2RRM(op1, op2, width.bytes).imm(op3, width, imm)
		}
	}

	/**
	 *     A8    TEST  A_I   1111
	 *     F6/0  TEST  RM_I  1111
	 *     84    TEST  RM_R  1111
	 */
	private fun encodeTEST(op1: OpNode, op2: OpNode) {
		when(op2.type) {
			REG -> Enc { 0x84 + R1111 }.encode2RMR(op1, op2.reg, 0)
			MEM -> Enc { 0x84 + R1111 }.encode2RMR(op2, op1.reg, 0)
			IMM -> if(op1.reg.isA) {
				Enc { 0xA8 + R1111 }.encodeNone(op1.reg.width).imm(op2, op1.reg.immWidth())
			} else {
				val width = op1.immWidth()
				Enc { 0xF6 + R1111 + EXT0 }.encode1RM(op1, width.bytes).imm(op2, width)
			}
		}
	}



	/*
	ADD/OR/ADC/SBB/AND/SUB/XOR/CMP encodings
	 */



	private fun encodeADD(start: Int, ext: Int, op1: OpNode, op2: OpNode) {
		when(op1.type) {
			REG -> when(op2.type) {
				REG -> Enc { start + R1111 }.encode2RR(op2.reg, op1.reg)
				MEM -> Enc { start + 2 + R1111 }.encode2RM(op1.reg, op2, 0)
				IMM -> {
					val imm = resolveImm(op2.node)

					fun ai() = Enc { start + 4 + R1111 }.encodeNone(op1.reg.width).imm(op2, op1.immWidth(), imm)
					fun i8() = Enc { 0x83 + R1110 + ext }.encode1R(op1.reg).imm(op2, BYTE, imm)
					fun i() = Enc { 0x80 + R1111 + ext }.encode1R(op1.reg).imm(op2, op1.immWidth(), imm)

					when {
						op1.reg == Reg.AL -> ai()
						op1.width == BYTE -> i()
						op2.width == BYTE -> i8()
						op2.width != null -> i()
						hasImmReloc       -> i()
						imm.isImm8        -> i8()
						op1.reg.isA       -> ai()
						else              -> i()
					}
				}
			}
			MEM -> when(op2.type) {
				REG -> Enc { start + R1111 }.encode2RM(op2.reg, op1, 0)
				MEM -> invalid()
				IMM -> {
					val width = op1.immWidth()
					val imm = resolveImm(op2.node)

					fun i8() = Enc { 0x83 + R1110 + ext }.encode1M(op1, 1).imm(op2, BYTE, imm)
					fun i() = Enc { 0x80 + R1111 + ext }.encode1M(op1, width.bytes).imm(op2, width, imm)

					when {
						width == BYTE -> i()
						op2.width == BYTE -> i8()
						op2.width != null -> i()
						hasImmReloc -> i()
						imm.isImm8 -> i8()
						else -> i()
					}
				}
			}
			IMM -> invalid()
		}
	}



	/*
	ROL/ROR/RCL/RCR/SHL/SHR/SAR encodings
	 */



	private fun encodeROL(ext: Int, op1: OpNode, op2: OpNode) {
		when(op1.type) {
			REG -> when(op2.type) {
				REG -> when {
					op2.reg != Reg.CL -> invalid()
					else -> Enc { 0xD2 + R1111 + ext }.encode1R(op1.reg)
				}
				IMM -> {
					val imm = resolveImm(op2)
					if(!hasImmReloc && imm == 1L)
						Enc { 0xD0 + R1111 + ext }.encode1R(op1.reg)
					else
						Enc { 0xC0 + R1111 + ext }.encode1R(op1.reg).imm(op2, BYTE, imm)
				}
				MEM -> invalid()
			}
			MEM -> when(op2.type) {
				REG -> when {
					op2.reg != Reg.CL -> invalid()
					else -> Enc { 0xD2 + R1111 + ext }.encode1M(op1, 0)
				}
				IMM -> {
					val imm = resolveImm(op2)
					if(!hasImmReloc && imm == 1L)
						Enc { 0xD0 + R1111 + ext }.encode1M(op1, 0)
					else
						Enc { 0xC0 + R1111 + ext }.encode1M(op1, 1).imm(op2, BYTE, imm)
				}
				MEM -> invalid()
			}
			IMM -> invalid()
		}
	}



	/*
	MOV encodings
	 */



	private fun encodeMOVRR(opcode: Int, op1: Reg, op2: Reg) {
		if(op2.type != RegType.R64) invalid()
		writeRex(0, op1.rex, 0, op2.rex)
		word(opcode)
		writeModRM(0b11, op1.value, op2.value)
	}



	private fun encodeMOVRSEG(opcode: Int, op1: Reg, op2: Reg) {
		when(op2.type) {
			RegType.R16 -> { byte(0x66); writeRex(0, op1.rex, 0, op2.rex) }
			RegType.R32 -> writeRex(0, op1.rex, 0, op2.rex)
			RegType.R64 -> writeRex(1, op1.rex, 0, op2.rex)
			else        -> invalid()
		}
		byte(opcode)
		writeModRM(0b11, op1.value, op2.value)
	}



	/**
	 *     88     MOV  RM_R   1111
	 *     8B     MOV  R_RM   1111
	 *     B0     MOV  O_I    1111
	 * 	   C6     MOV  RM_I   1111
	 *     8C     MOV  R_SEG  1110
	 *     8C     MOV  M_SEG  0010
	 *     8E     MOV  SEG_R  1110
	 *     8E     MOV  SEG_M  0010
	 *     A0     MOV  A_MOF  1111
	 *     A2     MOV  MOF_A  1111
	 *     0F 20  MOV  R_CR   1000
	 *     0F 21  MOV  R_DR   1000
	 *     0F 22  MOV  CR_R   1000
	 *     0F 23  MOV  DR_R   1000
	 */
	private fun encodeMOV(op1: OpNode, op2: OpNode) {
		when(op1.type) {
			REG -> when(op2.type) {
				REG -> when {
					op1.reg.isR && op2.reg.isR  -> Enc { 0x88 + R1111 }.encode2RR(op2.reg, op1.reg)
					op1.reg.type == RegType.CR  -> encodeMOVRR(0x220F, op1.reg, op2.reg)
					op2.reg.type == RegType.CR  -> encodeMOVRR(0x200F, op2.reg, op1.reg)
					op1.reg.type == RegType.DR  -> encodeMOVRR(0x230F, op1.reg, op2.reg)
					op2.reg.type == RegType.DR  -> encodeMOVRR(0x210F, op2.reg, op1.reg)
					op1.reg.type == RegType.SEG -> encodeMOVRSEG(0x8E, op1.reg, op2.reg)
					op2.reg.type == RegType.SEG -> encodeMOVRSEG(0x8C, op2.reg, op1.reg)
					else -> invalid()
				}
				MEM -> when(op1.reg.type) {
					RegType.SEG -> Enc { 0x8C + R0010 }.encode2RM(op1.reg, op2, 0)
					else        -> Enc { 0x8B + R1111 }.encode2RM(op1.reg, op2, 0)
				}
				IMM -> Enc { 0xB0 + R1111 }.encode1O(op1.reg).imm(op2, op1.width!!)
			}
			MEM -> when(op2.type) {
				REG -> when(op2.reg.type) {
					RegType.SEG -> Enc { 0x8E+R0010+MM }.encode2RM(op2.reg, op1, 0)
					else        -> Enc { 0x88+R1111+MM }.encode2RM(op2.reg, op1, 0)
				}
				IMM -> {
					val width = op1.immWidth()
					Enc { 0xC6 + R1111 }.encode1M(op1, width.bytes).imm(op2, width)
				}
				MEM -> invalid()
			}
			IMM -> invalid()
		}
	}



	/*
	REL encodings
	 */



	private fun encodeCALL(op1: OpNode) {
		when(op1.type) {
			REG -> Enc { 0xFF + R1000 + EXT2 }.encode1R(op1.reg)
			MEM -> Enc { 0xFF + R1000 + EXT2 }.encode1M(op1, 0)
			IMM -> encode1Rel(0xE8, DWORD, op1)
		}
	}

	private fun encodeJMP(op1: OpNode) {
		when(op1.type) {
			REG -> Enc { 0xFF + R1000 + EXT4 }.encode1R(op1.reg)
			MEM -> Enc { 0xFF + R1000 + EXT4 }.encode1M(op1, 0)
			IMM -> encode1Rel832(0xEB, 0xE9, op1)
		}
	}

	private fun encode1Rel(opcode: Int, width: Width, op1: OpNode) =
		byte(opcode).rel(op1, width)

	private fun encode1Rel832(rel8Opcode: Int, rel32Opcode: Int, op1: OpNode) {
		val imm = resolveImm(op1)

		fun rel8() = byte(rel8Opcode).rel(op1, BYTE, imm)
		fun rel32() = writer.varLengthInt(rel32Opcode).rel(op1, DWORD, imm)

		when(op1.width) {
			null -> when {
				hasImmReloc -> rel32()
				imm.isImm8  -> rel8()
				else        -> rel32()
			}
			BYTE  -> rel8()
			DWORD -> rel32()
			else  -> invalid()
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
	private fun encodePUSH(op1: OpNode) {
		when(op1.type) {
			REG -> when(op1.reg) {
				Reg.FS -> word(0xA00F)
				Reg.GS -> word(0xA80F)
				else   -> Enc { 0x50 + R1010 }.encode1O(op1.reg)
			}
			MEM -> Enc { 0xFF + R1010 + EXT6 }.encode1M(op1, 0)
			IMM -> {
				val imm = resolveImm(op1)

				fun i32() = byte(0x68).imm(op1, DWORD, imm)
				fun i16() = word(0x6866).imm(op1, WORD, imm)
				fun i8() = byte(0x6A).imm(op1, BYTE, imm)

				when(op1.width) {
					null -> when {
						hasImmReloc -> i32()
						imm.isImm8  -> i8()
						imm.isImm16 -> i16()
						else        -> i32()
					}
					BYTE  -> i8()
					WORD  -> i16()
					DWORD -> i32()
					else  -> invalid()
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
	private fun encodePOP(op1: OpNode) {
		when(op1.type) {
			REG -> when(op1.reg) {
				Reg.FS -> word(0xA10F)
				Reg.GS -> word(0xA90F)
				else -> Enc { 0x58 + R1010 }.encode1O(op1.reg)
			}
			MEM -> Enc { 0x8F + R1010 + EXT0 }.encode1M(op1, 0)
			else -> invalid()
		}
	}



	/*
	Pseudo-mnemonics
	 */



	private fun encodeDLLCALL(op1: OpNode) {
		val nameNode = op1.node as? NameNode ?: invalid()
		nameNode.symbol = context.getDllImport(nameNode.name)
		if(nameNode.symbol == null) invalid("Unrecognised dll import: ${nameNode.name}")
		Enc { 0xFF + R1000 + EXT2 }.encode1M(OpNode.mem(QWORD, nameNode), 0)
	}

	private fun encodeRETURN() {
		if(epilogueWriter.isEmpty)
			writer.i8(0xC3)
		else
			writer.bytes(epilogueWriter)
	}
	
	
	
	/*
	FPU Encodings
	 */
	

	
	/** FADD/FMUL/FCOM/FCOMP/FSUB/FSUBR/FDIV/FDIVR */
	private fun encodeFADD1(opcode: Int, extension: Int, op1: OpNode) {
		when(op1.type) {
			MEM -> when(op1.width) {
				DWORD -> encode1MAny(0xD8, extension, op1)
				QWORD -> encode1MAny(0xDC, extension, op1)
				else -> invalid()
			}
			REG -> word(opcode + (op1.asST.value shl 8))
			else -> invalid()
		}
	}
	
	private fun encode2STST(opcode1: Int, opcode2: Int, op1: OpNode, op2: OpNode) {
		when {
			op2.reg == Reg.ST0 && op1.isST -> word(opcode2 + (op1.reg.value shl 8))
			op1.reg == Reg.ST0 && op2.isST -> word(opcode1 + (op2.reg.value shl 8))
			else -> invalid()
		}
	}

	/** FIADD/FIMUL/FICOM/FICOMP/FISUB/FISUBR/FIDIV/FIDIVR */
	private fun encodeFIADD(ext: Int, op1: OpNode) {
		when(op1.width) {
			WORD  -> encode1MAny(0xDE, ext, op1)
			DWORD -> encode1MAny(0xDA, ext, op1)
			else  -> invalid()
		}
	}

	private fun encode2ST0STI(opcode: Int, op1: OpNode, op2: OpNode) {
		if(op2.reg.type != RegType.ST) invalid()
		if(op1.reg != Reg.ST0) invalid()
		word(opcode + (op2.reg.value shl 8))
	}

	private fun encode2STIST0(opcode: Int, op1: OpNode, op2: OpNode) {
		if(op1.reg.type != RegType.ST) invalid()
		if(op2.reg != Reg.ST0) invalid()
		word(opcode + (op1.reg.value shl 8))
	}

	private fun encode1ST(opcode: Int, op1: Reg) {
		word(opcode + (op1.value shl 8))
	}

	private fun encodeFCMOVCC(opcode: Int, op1: OpNode, op2: OpNode) {
		if(!op2.isST || op1.reg != Reg.ST0) invalid()
		writer.i16(opcode + (op2.reg.value shl 8))
	}



	/*
	SIMD
	 */



	private fun assembleSIMD(node: InsNode) {
		val size = when(node.size) {
			0 -> 0
			1 -> 1
			2 -> if(node.op2.isImm) 1 else 2
			3 -> if(node.op3.isImm) 2 else 3
			4 -> if(node.op4.isImm) 3 else 4
			else -> invalid()
		}

		when(size) {
			0 -> invalid()
			1 -> {

			}
			2 -> {
				when {
					node.op1.isMem -> { }
					node.op2.isMem -> { }
					else -> { }
				}
			}
			3 -> {

			}
			4 -> {

			}
			else -> invalid()
		}
	}



	/*
	SIMD utils
	 */



	private fun Reg.toOp(): Op = when(type) {
		RegType.R8  -> Op.R8
		RegType.R16 -> Op.R16
		RegType.R32 -> Op.R32
		RegType.R64 -> Op.R64
		RegType.MM  -> Op.MM
		RegType.X   -> Op.X
		RegType.Y   -> Op.Y
		RegType.Z   -> Op.Z
		RegType.K   -> Op.K
		RegType.T   -> Op.T
		RegType.ST  -> Op.ST
		else        -> invalid()
	}



	private fun OpNode.toOp(): Op = when(type) {
		REG -> when(reg.type) {
			RegType.R8  -> Op.R8
			RegType.R16 -> Op.R16
			RegType.R32 -> Op.R32
			RegType.R64 -> Op.R64
			RegType.MM  -> Op.MM
			RegType.X   -> Op.X
			RegType.Y   -> Op.Y
			RegType.Z   -> Op.Z
			RegType.K   -> Op.K
			RegType.T   -> Op.T
			RegType.ST  -> Op.ST
			else        -> invalid()
		}
		MEM -> when(width) {
			null  -> Op.MEM
			BYTE  -> Op.M8
			WORD  -> Op.M16
			DWORD -> Op.M32
			QWORD -> Op.M64
			TWORD -> Op.M80
			XWORD -> Op.M128
			YWORD -> Op.M256
			ZWORD -> Op.M512
		}
		IMM -> if(width == null || width == BYTE) Op.I8 else invalid()
	}



	private val multiVsibMnemonics = setOf(
		// RMR RM
		VPGATHERQD,
		VGATHERQPS,
		// MR
		VSCATTERQPS,
		VPSCATTERQD
	)



	private fun getVsib(node: OpNode): Int {
		var vsib = 0
		fun check(n: AstNode) { when(n) {
			is BinaryNode -> { check(n.left); check(n.right) }
			is UnaryNode -> check(n.node)
			is RegNode -> when(n.value.type) {
				RegType.X -> vsib = 1
				RegType.Y -> vsib = 2
				RegType.Z -> vsib = 3
				else -> { }
			}
			else -> { }
		}}
		check(node.node)
		return vsib
	}



	/*private fun getVsib(node: InsNode): Int = when {
		node.mnemonic !in multiVsibMnemonics -> 0
		node.op1 != null && node.op1.isMem -> getVsib(node.op1)
		node.op2 != null && node.op2.isMem -> getVsib(node.op2)
		node.op3 != null && node.op3.isMem -> getVsib(node.op3)
		node.op4 != null && node.op4.isMem -> getVsib(node.op4)
		else -> 0
	}



	private fun getEnc(
		node  : InsNode,
		i8    : Boolean,
		op1   : Op,
		op2   : Op      = Op.NONE,
		op3   : Op      = Op.NONE,
		op4   : Op      = Op.NONE,
		width : Width?  = null,
		vsib  : Int     = 0
	) : NasmEnc {
		val encs = EncGen.map[node.mnemonic] ?: invalid("Mnemonic has no encodings")
		val ops = SimdOps(i8, op1, op2, op3, op4, width, vsib)
		for(enc in encs)
			if(enc.simdOps == ops)
				return enc
		var memEnc: NasmEnc? = null
		if(width != null) invalid("No encodings found")
		for(enc in encs)
			if(enc.simdOps.equalsExceptMem(ops))
				memEnc = if(memEnc == null) enc else invalid("Ambiguous width")
		return memEnc ?: invalid("No encodings found")
	}



	private fun assembleSimd1(node: InsNode, i8: Boolean) {
		val op1 = node.op1!!
		if(op1.isMem) {
			val enc = getEnc(node, i8, Op.MEM, width = op1.width)
			enc.encodeAvx1M(op1, if(i8) 1 else 0)
		} else {
			val enc = getEnc(node, i8, op1.reg.toOp())
			enc.encodeAvx1M(op1, if(i8) 1 else 0)
		}
	}



	private fun assembleSimd2(node: InsNode, op1: OpNode, op2: OpNode, i8: Boolean) {

	}



	private fun assembleSimd3(node: InsNode, op1: OpNode, op2: OpNode, op3: OpNode, i8: Boolean) {

	}



	private fun assembleSimd4(node: InsNode, op1: OpNode, op2: OpNode, op3: OpNode, op4: OpNode) {
		if(op3.isMem)
			encodeAvx4RRRM(node, op1.reg, op2.reg, op4.reg, op3)
		else
			encodeAvx4RRRR(node, op1.reg, op2.reg, op3.reg, op4.reg)
	}



	private fun assembleSimd(node: InsNode) {
		when {
			node.op1 == null -> invalid()
			node.op2 == null -> assembleSimd1(node)
			node.op3 == null -> if(node.op2.isImm)
				assembleSimd1(enc, node.op1).imm8(node.op2)
			else
				assembleSimd2(enc, node.op1, node.op2)
			node.op4 == null -> if(node.op3.isImm)
				assembleSimd2(enc, node.op1, node.op2).imm8(node.op3)
			else
				assembleSimd3(enc, node.op1, node.op2, node.op3)
			else -> if(node.op4.isImm)
				assembleSimd3(enc, node.op1, node.op2, node.op3).imm8(node.op3)
			else
				assembleSimd4(node, node.op1, node.op2, node.op3, node.op4)
		}
	}*/



	/*
	Avx writing
	 */


	/*
	private fun writeSimdPrefix(enc: NasmEnc) { when(enc.prefix) {
		Prefix.NONE -> { }
		Prefix.P66  -> byte(0x66)
		Prefix.PF2  -> byte(0xF2)
		Prefix.PF3  -> byte(0xF3)
		else        -> invalid()
	}}



	private fun writeSimdOpcode(enc: NasmEnc) { when(enc.escape) {
		Escape.NONE -> byte(enc.opcode)
		Escape.E0F  -> word(0x0F or (enc.opcode shl 8))
		Escape.E38  -> i24(0x380F or (enc.opcode shl 16))
		Escape.E3A  -> i24(0x3A0F or (enc.opcode shl 16))
	}}



	/**
	 *     wvvv-vlpp_rxbm-mmmm_1100-0100
	 *     r: ~REX.R (ModRM:REG)
	 *     x: ~REX.X (SIB:INDEX)
	 *     b: ~REX.B (SIB:BASE, MODRM:RM, OPREG)
	 */
	private fun NasmEnc.writeVex(r: Int, x: Int, b: Int, vvvv: Int) {
		if(vexw.value != 0 || escape.avxValue > 1 || x == 0 || b == 0)
			dword(
				(0xC4 shl 0) or
					(r shl 15) or
					(x shl 14) or
					(b shl 13) or
					(escape.avxValue shl 8) or
					(vexw.value shl 23) or
					(vvvv shl 19) or
					(vexl.value shl 18) or
					(prefix.avxValue shl 16) or
					(opcode shl 24)
			)
		else
			i24(
				(0xC5 shl 0) or
					(r shl 15) or
					(vvvv shl 11) or
					(vexl.value shl 10) or
					(prefix.avxValue shl 8) or
					(opcode shl 16)
			)
	}



	private fun encodeAvx4RRRR(node: InsNode, op1: Reg, op2: Reg, op3: Reg, op4: Reg) {
		val enc = getEnc(node, false, op1.toOp(), op2.toOp(), op3.toOp(), op4.toOp())
		enc.writeVex(op1.vexRex, 1, op2.vexRex, op2.vvvvValue)
		writeModRM(0b11, op1.value, op3.value)
		byte(op4.index shl 4)
	}



	private fun encodeAvx4RRRM(node: InsNode, r1: Reg, r2: Reg, r3: Reg, m: OpNode) {
		val enc = getEnc(node, false, r1.toOp(), r2.toOp(), Op.MEM, r3.toOp(), m.width)
		val mem = resolveMem(m)
		enc.writeVex(r1.vexRex, mem.vexX, mem.vexB, r2.vvvvValue)
		mem.write(r1.value, 0)
		byte(r3.index shl 4)
	}



	private fun NasmEnc.encodeSseRR(op1: Reg, op2: Reg) {
		if(o16 == 1) byte(0x66)
		writeSimdPrefix(this)
		if(simdOpEnc != SimdOpEnc.MR) {
			writeRex(rw, op1.rex, 0, op2.rex)
			writeSimdOpcode(this)
			writeModRM(0b11, op1.value, op2.value)
		} else {
			writeRex(rw, op2.rex, 0, op1.rex)
			writeSimdOpcode(this)
			writeModRM(0b11, op2.value, op1.value)
		}
	}



	private fun NasmEnc.encodeSseRM(op1: Reg, op2: OpNode, op3: OpNode?) {
		val mem = resolveMem(op2)
		writeA32(mem)
		if(o16 == 1) byte(0x66)
		writeSimdPrefix(this)
		writeRex(rw, op1.rex, mem.rexX, mem.rexB)
		writeSimdOpcode(this)
		if(op3 != null)
			mem.write(op1.value, 1).imm8(op3)
		else
			mem.write(op1.value, 0)
	}



	private fun NasmEnc.encodeSseRI(op1: Reg, op2: OpNode) {
		if(o16 == 1) byte(0x66)
		writeSimdPrefix(this)
		writeRex(rw, 0, 0, op1.rex)
		writeSimdOpcode(this)
		writeModRM(0b11, ext, op1.value)
		writeImm(op2, BYTE)
	}



	private fun NasmEnc.encodeAvx3RRM(r1: Reg, r2: Reg, mem: Mem, immWidth: Int) {
		if(simdOpEnc == SimdOpEnc.MV) {
			writeVex(r2.vexRex, mem.vexX, mem.vexB, r1.vvvvValue)
			mem.write(r2.value, immWidth)
		} else {
			writeVex(r1.vexRex, mem.vexX, mem.vexB, r2.vvvvValue)
			mem.write(r1.value, immWidth)
		}
	}




	private fun NasmEnc.encodeAvxRRR(r: Reg, m: Reg, v: Reg) {
		writeVex(r.vexRex, 1, m.vexRex, v.vvvvValue)
		writeModRM(0b11, r.value, m.value)
	}



	private fun encodeAvx2RR(enc: NasmEnc, op1: Reg, op2: Reg) {
		when(enc.simdOpEnc) {
			SimdOpEnc.VM -> enc.encodeAvxRRR(Reg.r8(enc.ext), op2, op1)
			SimdOpEnc.RM -> enc.encodeAvxRRR(op1, op2, Reg.RAX)
			SimdOpEnc.MR -> enc.encodeAvxRRR(op2, op1, Reg.RAX)
			else         -> invalid()
		}
	}



	private fun encodeAvx3RRR(enc: NasmEnc, op1: Reg, op2: Reg, op3: Reg) {
		when(enc.simdOpEnc) {
			SimdOpEnc.RV -> enc.encodeAvxRRR(op1, op3, op2)
			SimdOpEnc.VM -> enc.encodeAvxRRR(op3, op2, op1)
			SimdOpEnc.MV -> enc.encodeAvxRRR(op2, op3, op1)
			SimdOpEnc.RM -> enc.encodeAvxRRR(op1, op2, op3)
			else         -> invalid()
		}
	}



	private fun NasmEnc.encodeAvx1R(op1: Reg) {
		if(opEnc != OpEnc.R) invalid()
		writeVex(op1.vexRex, 1, 1, 0b1111)
		writeModRM(0b11, op1.value, 0)
	}



	private fun encodeAvx1M(node: InsNode, op1: OpNode, immWidth: Int) {
		val enc = getEnc(node, )
		val mem = resolveMem(op1)
		writeVex(this, 1, mem.vexX, mem.vexB, 0b1111)
		mem.write(ext, immWidth)
	}




	private fun encodeAvx2RM(enc: NasmEnc, op1: Reg, op2: OpNode, immWidth: Int) {
		val mem = resolveMem(op2)
		if(enc.simdOpEnc == SimdOpEnc.MR || enc.simdOpEnc == SimdOpEnc.RM) {
			writeVex(enc, op1.vexRex, mem.vexX, mem.vexB, 0b1111)
			mem.write(op1.value, immWidth)
		} else if(enc.simdOpEnc == SimdOpEnc.VM) {
			writeVex(enc, 1, mem.vexX, mem.vexB, op1.vvvvValue)
			mem.write(enc.ext, immWidth)
		} else {
			invalid()
		}
	}
	*/


}