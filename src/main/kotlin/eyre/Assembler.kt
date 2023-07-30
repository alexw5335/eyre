package eyre

import eyre.util.NativeWriter
import eyre.Width.*
import eyre.OpNodeType.*
import eyre.Mnemonic.*
import eyre.gen.EncGen
import eyre.gen.NasmEnc

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
	}.let(::error)



	private fun invalid(string: String): Nothing = error("$string: ${currentIns.printString}")



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
		
		when {
			node.size == 0 -> assemble0(node)
			node.mnemonic in customMnemonics -> customMnemonics[node.mnemonic]!!(node)
			else -> assembleSimd(node)
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


	@Suppress("UnusedReceiverParameter")
	private fun Any.rel(node: OpNode, width: Width, value: Long = resolveImm(node.node)) =
		writeRel(node, width, value)

	@Suppress("UnusedReceiverParameter")
	private fun Any.imm(node: OpNode, width: Width, value: Long = resolveImm(node.node)) =
		writeImm(node, width, value)



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



	private inline fun Enc(block: Enc.Companion.() -> Int) = Enc(block(Enc))

	private fun OpNode.immWidth() = when(width) {
		null  -> invalid()
		QWORD -> DWORD
		else  -> width
	}

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

	private fun Enc.checkWidth(width: Width) {
		if((1 shl width.ordinal) and mask == 0) invalid()
	}

	private fun Enc.writeO16(width: Width) {
		if(mask != 0b10 && width == WORD) writer.i8(0x66)
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
	private fun Enc.rexw(width: Width) =
		(width.bytes shr 3) and (mask shr 2)

	/** Add one if width is not BYTE and if widths has BYTE set */
	private fun getOpcode(opcode: Int, mask: Int, width: Width) =
		opcode + ((mask and 1) and (1 shl width.ordinal).inv())

	private fun Enc.writePrefix() { when(prefix) {
		0 -> { }
		1 -> byte(0x66)
		2 -> byte(0xF2)
		3 -> byte(0xF3)
		4 -> byte(0x9B)
	} }

	private fun Enc.writeEscape() { when(escape) {
		0 -> { }
		1 -> byte(0x0F)
		2 -> word(0x380F)
		3 -> word(0x3A0F)
	} }

	private fun Enc.writeOpcode(width: Width) {
		writeEscape()
		writer.varLengthInt(getOpcode(opcode, mask, width))
	}



	/*
	Encoding
	 */



	private fun encode1I(opcode: Int, imm: OpNode, width: Width) {
		when(width) {
			BYTE  -> byte(opcode).imm(imm, BYTE)
			WORD  -> word(((opcode + 1) shl 8) or 0x66).imm(imm, WORD)
			DWORD -> byte(opcode + 1).imm(imm, DWORD)
			QWORD -> word(((opcode + 1) shl 8) or 0x48).imm(imm, DWORD)
			else  -> invalid()
		}
	}

	private fun Enc.encode1M(op1: OpNode, immLength: Int) {
		val mem = resolveMem(op1)

		if(mask.countOneBits() == 1) {
			if(op1.width != null) checkWidth(op1.width)
			writeA32(mem)
			writePrefix()
			writeRex(rexw, 0, mem.rexX, mem.rexB)
			writeEscape()
			writer.varLengthInt(opcode)
			mem.write(ext, immLength)
		} else {
			val width = op1.width ?: invalid()
			checkWidth(width)
			writeO16(width)
			writeA32(mem)
			writePrefix()
			writeRex(rexw or rexw(width), 0, mem.rexX, mem.rexB)
			writeOpcode(width)
			mem.write(ext, immLength)
		}
	}

	private fun Enc.encode1R(op1: Reg) {
		val width = op1.width
		checkWidth(width)
		writeO16(width)
		writePrefix()
		writeRex(rexw or rexw(width), 0, 0, op1.rex, op1.rex8, op1.noRex)
		writeOpcode(width)
		writeModRM(0b11, this.ext, op1.value)
	}

	private fun Enc.encode1O(op1: Reg) {
		val mask = this.mask
		val width = op1.width
		checkWidth(width)
		writeO16(width)
		writeRex(rexw or rexw(width), 0, 0, op1.rex, op1.rex8, op1.noRex)
		writeEscape()
		// Only single-byte opcodes use this encoding
		val opcode = this.opcode + (((mask and 1) and (1 shl width.ordinal).inv()) shl 3) + op1.value
		byte(opcode)
	}

	private fun Enc.encode2RM(op1: Reg, op2: OpNode, immLength: Int) {
		val width = op1.width
		if(mm == 0 && op2.width != null && op2.width != op1.width) invalid()
		val mem = resolveMem(op2)
		checkWidth(width)
		writeO16(width)
		writeA32(mem)
		writePrefix()
		writeRex(rexw or rexw(width), op1.rex, mem.rexX, mem.rexB, op1.rex8, op1.noRex)
		writeOpcode(width)
		mem.write(op1.value, immLength)
	}

	private fun Enc.encode2RR(op1: Reg, op2: Reg) {
		val width = op1.width
		if(mm == 0 && op1.width != op2.width) invalid()
		checkWidth(width)
		writeO16(width)
		writePrefix()
		writeRex(rexw or rexw(width), op1.rex, 0, op2.rex, op1.rex8 or op2.rex8, op1.noRex or op2.noRex)
		writeOpcode(width)
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



	private fun assemble0(node: InsNode) {
		if(node.mnemonic == TILERELEASE) {
			writer.i40(0xC04978E2C4)
		} else if(node.mnemonic == RETURN) {
			encodeRETURN()
		} else {
			val opcode = Encs.ZERO_OP_OPCODES[node.mnemonic.ordinal]
			if(opcode == 0) invalid()
			writer.varLengthInt(opcode)
		}
	}



	private val InsNode.single get() = if(size != 1) invalid() else op1



	private val customMnemonics = mapOf<Mnemonic, (InsNode) -> Unit>(
		PUSH    to ::encodePUSH,
		POP     to ::encodePOP,
		IN      to ::encodeIN,
		OUT     to ::encodeOUT,
		MOV     to ::encodeMOV,
		XCHG    to ::encodeXCHG,
		TEST    to ::encodeTEST,
		IMUL    to ::encodeIMUL,
		PUSHW   to ::encodePUSHW,
		POPW    to ::encodePOPW,
		CALL    to ::encodeCALL,
		JMP     to ::encodeJMP,
		FSTSW   to ::encodeFSTSW,
		FNSTSW  to ::encodeFNSTSW,
		LEA     to ::encodeLEA,
		JO      to { encodeJCC(0x70, 0x800F, it) },
		JNO     to { encodeJCC(0x71, 0x810F, it) },
		JB      to { encodeJCC(0x72, 0x820F, it) },
		JNAE    to { encodeJCC(0x72, 0x820F, it) },
		JC      to { encodeJCC(0x72, 0x820F, it) },
		JNB     to { encodeJCC(0x73, 0x830F, it) },
		JAE     to { encodeJCC(0x73, 0x830F, it) },
		JNC     to { encodeJCC(0x73, 0x830F, it) },
		JZ      to { encodeJCC(0x74, 0x840F, it) },
		JE      to { encodeJCC(0x74, 0x840F, it) },
		JNZ     to { encodeJCC(0x75, 0x850F, it) },
		JNE     to { encodeJCC(0x75, 0x850F, it) },
		JBE     to { encodeJCC(0x76, 0x860F, it) },
		JNA     to { encodeJCC(0x76, 0x860F, it) },
		JNBE    to { encodeJCC(0x77, 0x870F, it) },
		JA      to { encodeJCC(0x77, 0x870F, it) },
		JS      to { encodeJCC(0x78, 0x880F, it) },
		JNS     to { encodeJCC(0x79, 0x890F, it) },
		JP      to { encodeJCC(0x7A, 0x8A0F, it) },
		JPE     to { encodeJCC(0x7A, 0x8A0F, it) },
		JNP     to { encodeJCC(0x7B, 0x8B0F, it) },
		JPO     to { encodeJCC(0x7B, 0x8B0F, it) },
		JL      to { encodeJCC(0x7C, 0x8C0F, it) },
		JNGE    to { encodeJCC(0x7C, 0x8C0F, it) },
		JNL     to { encodeJCC(0x7D, 0x8D0F, it) },
		JGE     to { encodeJCC(0x7D, 0x8D0F, it) },
		JLE     to { encodeJCC(0x7E, 0x8E0F, it) },
		JNG     to { encodeJCC(0x7E, 0x8E0F, it) },
		JNLE    to { encodeJCC(0x7F, 0x8F0F, it) },
		JG      to { encodeJCC(0x7F, 0x8F0F, it) },
		NOT     to { Enc { 0xF6+EXT2+R1111 }.encode1RM(it.single, 0) },
		NEG     to { Enc { 0xF6+EXT3+R1111 }.encode1RM(it.single, 0) },
		MUL     to { Enc { 0xF6+EXT4+R1111 }.encode1RM(it.single, 0) },
		DIV     to { Enc { 0xF6+EXT6+R1111 }.encode1RM(it.single, 0) },
		IDIV    to { Enc { 0xF6+EXT7+R1111 }.encode1RM(it.single, 0) },
		INC     to { Enc { 0xFE+EXT0+R1111 }.encode1RM(it.single, 0) },
		DEC     to { Enc { 0xFE+EXT1+R1111 }.encode1RM(it.single, 0) },
		NOP     to { Enc { E0F+0x1F+R1110 }.encode1RM(it.single, 0) },
		RET     to { byte(0xC2).imm(it.single.asImm, WORD) },
		RETF    to { byte(0xCA).imm(it.single.asImm, WORD) },
		RETW    to { word(0xC266).imm(it.single.asImm, WORD) },
		RETFQ   to { word(0xCA48).imm(it.single.asImm, WORD) },
		INT     to { byte(0xCD).imm(it.single.asImm, BYTE) },
		LOOP    to { encode1Rel(0xE2, BYTE, it.single.asImm) },
		LOOPE   to { encode1Rel(0xE1, BYTE, it.single.asImm) },
		LOOPZ   to { encode1Rel(0xE1, BYTE, it.single.asImm) },
		LOOPNE  to { encode1Rel(0xE0, BYTE, it.single.asImm) },
		LOOPNZ  to { encode1Rel(0xE0, BYTE, it.single.asImm) },
		JMPF    to { Enc { 0xFF+R1110+EXT5 }.encode1M(it.single, 0) },
		CALLF   to { Enc { 0xFF+R1110+EXT3 }.encode1M(it.single, 0) },
		JECXZ   to { byte(0x67); encode1Rel(0xE3, BYTE, it.single.asImm) },
		JRCXZ   to { encode1Rel(0xE3, BYTE, it.single) },
		DLLCALL to { encodeDLLCALL(it.single.asImm) },
		BSWAP   to { Enc { E0F+0xC8+R1100 }.encode1O(it.single.reg) },
		XABORT  to { dword(0xF8C6).imm(it.single.asImm, BYTE) },
		XBEGIN  to { dword(0xF8C7).rel(it.single.asImm, DWORD) },
		HRESET  to { writer.i40(0xC0F03A0FF3L).imm(it.single.asImm, BYTE) },
		SHLD    to { encodeSHLD(0xA4, it) },
		SHRD    to { encodeSHLD(0xAC, it) },
		ADD     to { encodeADD(0x00, Enc.EXT0, it) },
		OR      to { encodeADD(0x08, Enc.EXT1, it) },
		ADC     to { encodeADD(0x10, Enc.EXT2, it) },
		SBB     to { encodeADD(0x18, Enc.EXT3, it) },
		AND     to { encodeADD(0x20, Enc.EXT4, it) },
		SUB     to { encodeADD(0x28, Enc.EXT5, it) },
		XOR     to { encodeADD(0x30, Enc.EXT6, it) },
		CMP     to { encodeADD(0x38, Enc.EXT7, it) },
		ROL     to { encodeROL(Enc.EXT0, it) },
		ROR     to { encodeROL(Enc.EXT1, it) },
		RCL     to { encodeROL(Enc.EXT2, it) },
		RCR     to { encodeROL(Enc.EXT3, it) },
		SAL     to { encodeROL(Enc.EXT4, it) },
		SHL     to { encodeROL(Enc.EXT4, it) },
		SHR     to { encodeROL(Enc.EXT5, it) },
		SAR     to { encodeROL(Enc.EXT7, it) },
	)



	/*
	Misc. encodings
	 */



	private fun encodeXCHG(node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2
		when {
			op1.reg == Reg.EAX && op2.reg == Reg.EAX -> word(0xC087)
			op1.isMem         -> Enc { 0x86 + R1111 }.encode2RM(op2.reg, op1, 0)
			op2.isMem         -> Enc { 0x86 + R1111 }.encode2RM(op1.reg, op2, 0)
			op1.width == BYTE -> Enc { 0x86 + R1111 }.encode2RR(op1.reg, op2.reg)
			op1.reg.isA       -> Enc { 0x90 + R1110 }.encode1O(op2.reg)
			op2.reg.isA       -> Enc { 0x90 + R1110 }.encode1O(op1.reg)
			else              -> Enc { 0x86 + R1111 }.encode2RR(op1.reg, op2.reg)
		}
	}



	private fun encodeLEA(node: InsNode) {
		if(node.size != 2) invalid()
		Enc { 0x8D + R1110 }.encode2RM(node.op1.reg, node.op2.asMem, 0)
	}



	private fun encodeFSTSW(node: InsNode) {
		val op1 = node.single
		when {
			op1.reg == Reg.AX -> writer.i24(0xE0DF9B)
			op1.isMem         -> Enc { P9B+0xDD+EXT7+R0010 }.encode1M(op1, 0)
			else              -> invalid()
		}
	}

	private fun encodeFNSTSW(node: InsNode) {
		val op1 = node.single
		when {
			op1.reg == Reg.AX -> writer.i16(0xE0DF)
			op1.isMem         -> Enc { 0xDD+EXT7+R0010 }.encode1M(op1, 0)
			else              -> invalid()
		}
	}

	private fun encodeIN(node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1.reg
		val op2 = node.op2

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

	private fun encodeOUT(node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2.reg
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
	private fun encodeSHLD(opcode: Int, node: InsNode) {
		if(node.size != 3) invalid()
		val op1 = node.op1
		val op2 = node.op2.reg
		val op3 = node.op3
		when {
			op3.reg == Reg.CL -> Enc { E0F+opcode+1+R1110 }.encode2RMR(op1, op2, 0)
			op3.type == IMM -> Enc { E0F+opcode+R1110 }.encode2RMR(op1, op2, 1).imm(op3, BYTE)
			else -> invalid()
		}
	}

	/**
	 *     F6/5   IMUL  RM       1111
	 *     0F AF  IMUL  R_RM     1110
	 *     6B     IMUL  R_RM_I8  1110
	 *     69     IMUL  R_RM_I   1110
	 */
	private fun encodeIMUL(node: InsNode) {
		val op1 = node.op1
		val op2 = node.op2
		val op3 = node.op3

		when(node.size) {
			1 -> Enc { 0xF6 + EXT5 + R1111 }.encode1RM(op1, 0)
			2 -> Enc { E0F + 0xAF + R1110 }.encode2RRM(op1.reg, op2, 0)
			3 -> {
				val imm = resolveImm(op3)

				if(op3.width == BYTE || (op3.width == null && !hasImmReloc && imm.isImm8)) {
					Enc { 0x6B + R1110 }.encode2RRM(op1.reg, op2, 1).imm(op3, BYTE, imm)
				} else {
					val width = if(op1.width == QWORD) DWORD else op1.reg.width
					Enc { 0x69 + R1110 }.encode2RRM(op1.reg, op2, width.bytes).imm(op3, width, imm)
				}
			}
			else -> invalid()
		}
	}

	/**
	 *     A8    TEST  A_I   1111
	 *     F6/0  TEST  RM_I  1111
	 *     84    TEST  RM_R  1111
	 */
	private fun encodeTEST(node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2

		when(op2.type) {
			REG -> Enc { 0x84 + R1111 }.encode2RMR(op1, op2.reg, 0)
			MEM -> Enc { 0x84 + R1111 }.encode2RMR(op2, op1.reg, 0)
			IMM -> if(op1.reg.isA) {
				encode1I(0xA8, op2, op1.reg.width)
			} else {
				val width = op1.immWidth()
				Enc { 0xF6 + R1111 + EXT0 }.encode1RM(op1, width.bytes).imm(op2, width)
			}
		}
	}



	/*
	ADD/OR/ADC/SBB/AND/SUB/XOR/CMP encodings
	 */



	private fun encodeADD(start: Int, ext: Int, node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2

		when(op1.type) {
			REG -> when(op2.type) {
				REG -> Enc { start + R1111 }.encode2RR(op2.reg, op1.reg)
				MEM -> Enc { start + 2 + R1111 }.encode2RM(op1.reg, op2, 0)
				IMM -> {
					val imm = resolveImm(op2.node)

					fun ai() = encode1I(start + 4, op2, op1.reg.width)
					fun i8() = Enc { 0x83+R1110+ext }.encode1R(op1.reg).imm(op2, BYTE, imm)
					fun i() = Enc { 0x80+R1111+ext }.encode1R(op1.reg).imm(op2, op1.immWidth(), imm)

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



	private fun encodeROL(ext: Int, node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2

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

	private fun encodeMOVMSEG(opcode: Int, op1: Reg, op2: OpNode) {
		val mem = resolveMem(op2)
		writeRex(if(op2.width == QWORD) 1 else 0, 0, mem.rexX, mem.rexB)
		byte(opcode)
		mem.write(op1.value, 0)
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
	private fun encodeMOV(node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2

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
					RegType.SEG -> encodeMOVMSEG(0x8E, op1.reg, op2)
					else        -> Enc { 0x8A+R1111 }.encode2RM(op1.reg, op2, 0)
				}
				IMM -> Enc { 0xB0 + R1111 }.encode1O(op1.reg).imm(op2, op1.width!!)
			}
			MEM -> when(op2.type) {
				REG -> when(op2.reg.type) {
					RegType.SEG -> encodeMOVMSEG(0x8C, op2.reg, op1)
					else        -> Enc { 0x88+R1111 }.encode2RM(op2.reg, op1, 0)
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



	private fun encodeCALL(node: InsNode) {
		if(node.size != 1) invalid()
		val op1 = node.op1
		when(op1.type) {
			REG -> Enc { 0xFF + R1000 + EXT2 }.encode1R(op1.reg)
			MEM -> Enc { 0xFF + R1000 + EXT2 }.encode1M(op1, 0)
			IMM -> encode1Rel(0xE8, DWORD, op1)
		}
	}

	private fun encodeJMP(node: InsNode) {
		if(node.size != 1) invalid()
		val op1 = node.op1
		when(op1.type) {
			REG -> Enc { 0xFF + R1000 + EXT4 }.encode1R(op1.reg)
			MEM -> Enc { 0xFF + R1000 + EXT4 }.encode1M(op1, 0)
			IMM -> encode1Rel832(0xEB, 0xE9, op1)
		}
	}

	private fun encode1Rel(opcode: Int, width: Width, op1: OpNode) =
		byte(opcode).rel(op1, width)

	private fun encodeJCC(rel8Opcode: Int, rel32Opcode: Int, node: InsNode) {
		if(node.size != 1) invalid()
		encode1Rel832(rel8Opcode, rel32Opcode, node.op1)
	}
	
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
	private fun encodePUSH(node: InsNode) {
		if(node.size != 1) invalid()
		val op1 = node.op1
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
	private fun encodePOP(node: InsNode) {
		if(node.size != 1) invalid()
		val op1 = node.op1
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
	
	private fun encodePUSHW(node: InsNode) {
		if(node.size != 1) invalid()
		when(node.op1.reg) {
			Reg.FS -> writer.i24(0xA80F66)
			Reg.GS -> writer.i32(0xA80F66)
			else   -> invalid()
		}
	}

	private fun encodePOPW(node: InsNode) {
		if(node.size != 1) invalid()
		when(node.op1.reg) {
			Reg.FS -> writer.i24(0xA10F66)
			Reg.GS -> writer.i32(0xA10F66)
			else   -> invalid()
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
	SIMD
	 */



	private fun NasmEnc.writeSimdOpcode() { when(escape) {
		Escape.NONE -> byte(opcode)
		Escape.E0F  -> word(0x0F or (opcode shl 8))
		Escape.E38  -> i24(0x380F or (opcode shl 16))
		Escape.E3A  -> i24(0x3A0F or (opcode shl 16))
	} }



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



	private fun getEnc(mnemonic: Mnemonic, ops: SimdOps): NasmEnc? {
		val encs = EncGen.map[mnemonic] ?: return null

		for(e in encs)
			if(e.simdOps == ops)
				return e

		if(ops.width != 0)
			return null

		for(e in encs)
			if(e.simdOps.equalsExceptWidth(ops))
				return e

		return null
	}



	fun getEnc(node: InsNode): NasmEnc? {
		val ops = listOf(node.op1, node.op2, node.op3, node.op4)

		val simdOps = SimdOps(
			if(ops.last().isImm) 1 else 0,
			node.op1.type.ordinal,
			node.op2.type.ordinal,
			node.op3.type.ordinal,
			node.op4.type.ordinal,
			ops.firstOrNull { it.isMem }?.width?.let { it.ordinal + 1 } ?: 0,
			ops.indexOfFirst { it.isMem } + 1,
			ops.firstOrNull { it.isMem }?.let { resolveMem(it).vsib } ?: 0
		)

		return getEnc(node.mnemonic, simdOps)
	}



	private fun assembleST(node: InsNode, r1: Reg, r2: Reg) {
		if(node.size > 2) invalid()
		val encs = EncGen.map[node.mnemonic] ?: invalid()

		fun st0st() = encs.firstOrNull { it.ops.size == 2 && it.ops[0] == Op.ST0 && it.ops[1] == Op.ST }
		fun stst0() = encs.firstOrNull { it.ops.size == 2 && it.ops[0] == Op.ST && it.ops[1] == Op.ST0 }

		if(r2.type == RegType.ST) {
			when {
				r2 == Reg.ST0 -> {
					val enc = if(r1 == Reg.ST0)
						stst0() ?: st0st()
					else stst0()
					if(enc == null) invalid()
					word(enc.opcode + (r1.value shl 8))
				}
				r1 == Reg.ST0 -> {
					val enc = st0st() ?: invalid()
					word(enc.opcode + (r2.value shl 8))
				}
				else -> invalid()
			}
		} else {
			val enc = encs.firstOrNull { it.ops.size == 1 && it.ops[0] == Op.ST }
				?: invalid()
			word(enc.opcode + (r1.value shl 8))
		}
	}



	private fun assembleSimd(node: InsNode) {

		if(node.high == 1)
			invalid("EVEX not currently supported")

		val r1 = node.op1.reg
		val r2 = node.op2.reg
		val r3 = node.op3.reg
		val r4 = node.op4.reg
		val memIndex: Int
		val memWidth: Width?
		val mem: Mem?

		if(r1.type == RegType.ST) {
			assembleST(node, r1, r2)
			return
		}

		when {
			node.op1.isMem -> { memIndex = 1; memWidth = node.op1.width; mem = resolveMem(node.op1) }
			node.op2.isMem -> { memIndex = 2; memWidth = node.op2.width; mem = resolveMem(node.op2) }
			node.op3.isMem -> { memIndex = 3; memWidth = node.op3.width; mem = resolveMem(node.op3) }
			node.op4.isMem -> invalid()
			else           -> { memIndex = 0; memWidth = null; mem = null }
		}

		var i8Node: OpNode? = null
		var i8 = 0

		when(node.size) {
			2 -> if(node.op2.isImm) { i8Node = node.op2; i8 = 1 }
			3 -> if(node.op3.isImm) { i8Node = node.op3; i8 = 1 }
			4 -> if(node.op4.isImm) { i8Node = node.op4; i8 = 1 }
		}

		val ops = SimdOps(
			i8,
			r1.type.ordinal,
			r2.type.ordinal,
			r3.type.ordinal,
			r4.type.ordinal,
			memWidth?.let { it.ordinal + 1 } ?: 0,
			memIndex,
			mem?.vsib ?: 0
		)

		val enc = getEnc(node.mnemonic, ops) ?: invalid("No encodings found")


		var r: Reg
		val m: Reg
		val v: Reg

		when(enc.simdOpEnc) {
			SimdOpEnc.RMV -> { r = r1; m = r2; v = r3 }
			SimdOpEnc.RVM -> { r = r1; v = r2; m = r3 }
			SimdOpEnc.MRV -> { m = r1; r = r2; v = r3 }
			SimdOpEnc.MVR -> { m = r1; v = r2; r = r3 }
			SimdOpEnc.VMR -> { v = r1; m = r2; r = r3 }
		}

		if(enc.hasExt)
			r = Reg.r8(enc.ext)

		if(enc.avx) {
			if(mem != null) {
				enc.writeVex(r.vexRex, mem.vexX, mem.vexB, v.vValue)
				mem.write(r.value, i8)
			} else {
				enc.writeVex(r.vexRex, 1, m.vexRex, v.vValue)
				writeModRM(0b11, r.value, m.value)
			}
		} else {
			if(enc.o16 == 1) byte(0x66)
			if(enc.a32 == 1) byte(0x67)
			if(enc.prefix != Prefix.NONE) byte(enc.prefix.value)
			val rexBanned = r1.noRex or r2.noRex
			val rexRequired = r1.rex8 or r2.rex8
			if(mem != null) {
				writeRex(enc.rw, r.rex, mem.rexX, mem.rexB, rexRequired, rexBanned)
				enc.writeSimdOpcode()
				mem.write(r.value, i8)
			} else {
				writeRex(enc.rw, r.rex, 0, m.rex, rexRequired, rexBanned)
				enc.writeSimdOpcode()
				writeModRM(0b11, r.value, m.value)
			}
		}

		when {
			r4 != Reg.NONE  -> byte(r4.index shl 4)
			i8Node != null  -> writeImm(i8Node, BYTE)
			enc.pseudo >= 0 -> byte(enc.pseudo)
		}
	}


}