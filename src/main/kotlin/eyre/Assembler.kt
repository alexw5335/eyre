package eyre

import eyre.Width.*
import eyre.gen.EncGen
import eyre.gen.NasmEnc
import eyre.util.NativeWriter

class Assembler(private val context: CompilerContext) {


	private var writer = context.textWriter

	private var section = Section.TEXT

	private var currentIns: InsNode? = null

	private val assemblers = Array<(InsNode) -> Unit>(Mnemonic.entries.size) { ::assembleAuto }

	private val epilogueWriter = NativeWriter()



	init {
		populateAssemblers()
	}



	fun assemble() {
		for(srcFile in context.srcFiles) {
			for((index, node) in srcFile.nodes.withIndex()) {
				when(node) {
					is InsNode       -> assemble(node)
					is LabelNode     -> handleLabel(node.symbol)
					is DirectiveNode -> handleDirective(node, index, srcFile.nodes)
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
			context.dataWriter.align(8)
			s.section = Section.DATA
			s.pos = context.dataWriter.pos
			for(c in s.string) context.dataWriter.i8(c.code)
			writer.i8(0)
		}
	}



	private fun invalid(string: String = "Assembler error"): Nothing {
		error("$string: ${currentIns?.printString}")
	}



	/*
	Nodes
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



	private fun handleVarDb(node: VarDbNode) = sectioned(context.dataWriter, Section.DATA) {
		writer.align(8)

		node.symbol.pos = writer.pos

		for(part in node.parts) {
			for(value in part.nodes) {
				if(value is StringNode) {
					for(char in value.value)
						writer.writeWidth(part.width, char.code)
				} else {
					imm(resolveImm(value), part.width)
				}
			}
		}
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
			imm(resolveImm(node), width)
		}
	}



	private fun handleVarInit(node: VarInitNode) {
		sectioned(context.dataWriter, Section.DATA) {
			writer.align(8)
			node.symbol.pos = writer.pos
			writeInitialiser(node.initialiser, node.symbol.type)
		}
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
		if(node.symbol !is ProcSymbol)
			return

		if(epilogueWriter.isNotEmpty) {
			writer.bytes(epilogueWriter)
			epilogueWriter.reset()
		}

		node.symbol.size = writer.pos - node.symbol.pos
	}



	private fun handleDirective(node: DirectiveNode, index: Int, nodes: List<AstNode>) {
		if(index >= nodes.size) invalid()
		val next = nodes[index + 1]
		if(node.name == Names.DEBUG) {
			val name = node.value?.let { if(it is StringNode) it.value else invalid() }
			context.debugDirectives += when(next) {
				is ProcNode -> DebugDirective(name ?: next.symbol.name.string, writer.pos, Section.TEXT)
				is InsNode  -> DebugDirective(name ?: invalid(), writer.pos, Section.TEXT)
				else        -> invalid()
			}
		} else {
			invalid()
		}
	}



	private fun handleProc(node: ProcNode) {
		handleLabel(node.symbol)
		val regs = ArrayList<Reg>()
		var toAlloc = 40L

		for(n in node.stackNodes)
			if(n is RegNode)
				regs += n.value
			else
				toAlloc += resolveImmSimple(n)

		if(toAlloc.isImm8) {
			writer.i32(0xEC_83_48 or (toAlloc.toInt() shl 24))
		} else if(toAlloc.isImm32) {
			writer.i24(0xEC_81_48)
			writer.i32(toAlloc.toInt())
		} else {
			invalid()
		}

		for(r in regs)
			if(r.rex == 1)
				writer.i16(0x50_41 + (r.value shl 8))
			else
				writer.i8(0x50 + r.value)

		if(toAlloc.isImm8) {
			epilogueWriter.i32(0xC4_83_48 or (toAlloc.toInt() shl 24))
		} else if(toAlloc.isImm32) {
			epilogueWriter.i24(0xC4_81_48)
			epilogueWriter.i32(toAlloc.toInt())
		} else {
			invalid()
		}

		for(i in regs.size - 1 downTo 0) {
			val r = regs[i]
			if(r.rex == 1)
				epilogueWriter.i16(0x58_41 + (r.value shl 8))
			else
				epilogueWriter.i8(0x58 + r.value)
		}

		epilogueWriter.i8(0xC3)
	}



	/*
	Resolution
	 */



	private fun addLinkReloc(width: Width, node: AstNode, offset: Int, rel: Boolean) =
		context.linkRelocs.add(Reloc(writer.pos, section, node, width, offset, rel))

	private fun addAbsReloc(node: AstNode) =
		context.absRelocs.add(Reloc(writer.pos, section, node, QWORD, 0, false))



	private fun Mem.checkReg(reg: Reg) {
		when(reg.type) {
			OpType.R32 -> if(aso == 2) invalid() else aso = 1
			OpType.R64 -> if(aso == 1) invalid() else aso = 2
			OpType.X   -> vsib = 1
			OpType.Y   -> vsib = 2
			OpType.Z   -> vsib = 3
			else        -> invalid()
		}
	}



	private fun resolveRec(node: AstNode, mem: Mem, regValid: Boolean): Long {
		if(node is IntNode)
			return node.value

		if(node is UnaryNode)
			return node.calculate(regValid) { n, v -> resolveRec(n, mem, v) }

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
			return node.calculate(regValid) { n, v -> resolveRec(n, mem, v) }
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
			if(symbol is VarAliasSymbol) return resolveRec((symbol.node as VarAliasNode).value, mem, regValid)
			if(symbol is AliasRefSymbol) return resolveRec(symbol.value, mem, regValid) + symbol.offset
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



	private fun resolve(mem: Mem, node: AstNode, isImm: Boolean): Mem {
		if(node is OpNode) {
			mem.node = node.node
			mem.width = node.width
			mem.disp = resolveRec(node.node, mem, true)
		} else {
			mem.node = node
			mem.width = null
			mem.disp = resolveRec(node, mem, true)
		}

		if(isImm) {
			if(mem.hasBase || mem.hasIndex)
				invalid()
		} else {
			mem.postResolve()
		}

		return mem
	}



	private fun resolveMem(node: AstNode) =
		resolve(Mem(), node, false)



	private fun resolveImm(node: AstNode) =
		resolve(Mem(), node, true)



	private fun Any.rel(mem: Mem, width: Width) {
		if(mem.width != null && mem.width != width) invalid()
		if(mem.relocs != 0) addLinkReloc(width, mem.node, 0, true)
		writer.writeWidth(width, mem.disp)
	}



	private fun Any.imm(mem: Mem, width: Width) {
		if(mem.width != null && mem.width != width) invalid()
		if(mem.relocs == 1) {
			if(width != QWORD) invalid()
			addAbsReloc(mem.node)
			writer.advance(8)
		} else if(mem.relocs > 1) {
			addLinkReloc(width, mem.node, 0, false)
			writer.advance(width.bytes)
		} else if(!writer.writeWidth(width, mem.disp)) {
			invalid()
		}
	}



	private fun Any.rel(op: OpNode, width: Width) =
		rel(resolveMem(op), width)



	private fun Any.imm(op: OpNode, width: Width) =
		imm(resolveMem(op), width)



	private fun resolveImmSimple(n: AstNode): Long = when(n) {
		is IntNode    -> n.value
		is UnaryNode  -> n.op.calculate(resolveImmSimple(n))
		is BinaryNode -> n.op.calculate(resolveImmSimple(n.left), resolveImmSimple(n.right))
		else          -> invalid()
	}



	/*
	Manual encoding
	 */



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

	private fun checkWidth(mask: Int, width: Int) {
		if((1 shl width) and mask == 0) invalid()
	}

	private fun writeO16(mask: Int, width: Int) {
		if(mask != 0b10 && width == 1) writer.i8(0x66)
	}

	private fun writeA32(mem: Mem) {
		if(mem.vsib != 0) invalid("VSIB not valid here")
		if(mem.aso == 1) byte(0x67)
	}

	/** Return 1 if width is QWORD (3) and widths has DWORD (2) set, otherwise 0 */
	private fun rexw(mask: Int, width: Int) = ((1 shl width) shr 3) and (mask shr 2)

	/** Add one if width is not BYTE and if widths has BYTE set */
	private fun writeOpcode(opcode: Int, mask: Int, width: Int) {
		val addition = (mask and 1) and (1 shl width).inv()
		if(opcode and 0xFF00 != 0) word(opcode + (addition shl 8)) else byte(opcode + addition)
	}

	private fun Mem.write(reg: Int, immLength: Int) {
		fun reloc(mod: Int) {
			when {
				hasReloc -> { addLinkReloc(DWORD, node, 0, false); writer.i32(0) }
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
			byte((reg shl 3) or 0b101)
			addLinkReloc(DWORD, node, immLength, true)
			dword(0)
		} else { // Absolute 32-bit or empty memory operand
			word(0b00_100_101_00_000_100 or (reg shl 3))
			reloc(0b10)
		}
	}



	/*
	Manual Encoding
	 */



	private fun OpNode.immWidth() = when(width) {
		null  -> invalid()
		QWORD -> DWORD
		else  -> width
	}

	private fun encode1REL(opcode: Int, width: Width, node: InsNode) {
		if(node.size != 1) invalid()
		if(node.op1.type != OpType.IMM) invalid()
		writer.varLengthInt(opcode)
		rel(node.op1, width)
	}

	private fun encode1I(opcode: Int, width: Width, node: InsNode) {
		if(node.size != 1) invalid()
		if(node.op1.type != OpType.IMM) invalid()
		writer.varLengthInt(opcode)
		imm(node.op1, width)
	}

	private fun encode1I(opcode: Int, imm: OpNode, width: Width) {
		when(width) {
			BYTE  -> byte(opcode).imm(imm, BYTE)
			WORD  -> word(((opcode + 1) shl 8) or 0x66).imm(imm, WORD)
			DWORD -> byte(opcode + 1).imm(imm, DWORD)
			QWORD -> word(((opcode + 1) shl 8) or 0x48).imm(imm, DWORD)
			else  -> invalid()
		}
	}

	private fun encode1MSingle(opcode: Int, width: Width, ext: Int, op1: OpNode) {
		val mem = resolveMem(op1)
		if(op1.width != null && op1.width != width) invalid()
		writeA32(mem)
		writeRex(0, 0, mem.rexX, mem.rexB)
		writer.varLengthInt(opcode)
		mem.write(ext, 0)
	}

	private fun encode1M(opcode: Int, mask: Int, ext: Int, op1: OpNode, immLength: Int) {
		val mem = resolveMem(op1)
		val width = op1.width?.ordinal ?: invalid()
		checkWidth(mask, width)
		writeO16(mask, width)
		writeA32(mem)
		writeRex(rexw(mask, width), 0, mem.rexX, mem.rexB)
		writeOpcode(opcode, mask, width)
		mem.write(ext, immLength)
	}

	private fun encode1R(opcode: Int, mask: Int, ext: Int, op1: Reg) {
		val width = op1.type.ordinal
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(rexw(mask, width), 0, 0, op1.rex, op1.rex8, op1.noRex)
		writeOpcode(opcode, mask, op1.type.ordinal)
		byte(0b11_000_000 or (ext shl 3) or op1.value)
	}

	private fun encode1O(opcode: Int, mask: Int, op1: Reg) {
		val width = op1.type.ordinal
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(rexw(mask, width), 0, 0, op1.rex, op1.rex8, op1.noRex)
		byte(opcode + (((mask and 1) and (1 shl width).inv()) shl 3) + op1.value)
	}

	private fun encode2RM(opcode: Int, mask: Int, op1: Reg, op2: OpNode, immLength: Int) {
		val width = op1.type.ordinal
		if(op2.width != null && op2.width.ordinal != width) invalid()
		val mem = resolveMem(op2)
		checkWidth(mask, width)
		writeO16(mask, width)
		writeA32(mem)
		writeRex(rexw(mask, width), op1.rex, mem.rexX, mem.rexB, op1.rex8, op1.noRex)
		writeOpcode(opcode, mask, width)
		mem.write(op1.value, immLength)
	}

	private fun encode2RR(opcode: Int, mask: Int, op1: Reg, op2: Reg) {
		val width = op1.type.ordinal
		if(op1.type != op2.type) invalid()
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(rexw(mask, width), op1.rex, 0, op2.rex, op1.rex8 or op2.rex8, op1.noRex or op2.noRex)
		writeOpcode(opcode, mask, width)
		byte(0b11_000_000 or (op1.value shl 3) or (op2.value))
	}

	private fun encode1RM(opcode: Int, mask: Int, ext: Int, op1: OpNode, immLength: Int) {
		when(op1.type) {
			OpType.MEM -> encode1M(opcode, mask, ext, op1, immLength)
			else       -> encode1R(opcode, mask, ext, op1.reg)
		}
	}

	private fun encode2RRM(opcode: Int, mask: Int, op1: Reg, op2: OpNode, immLength: Int) {
		when(op2.type) {
			OpType.MEM -> encode2RM(opcode, mask, op1, op2, immLength)
			else       -> encode2RR(opcode, mask, op1, op2.reg)
		}
	}

	private fun encode2RMR(opcode: Int, mask: Int, op1: OpNode, op2: Reg, immLength: Int) {
		when(op1.type) {
			OpType.MEM -> encode2RM(opcode, mask, op2, op1, immLength)
			else -> encode2RR(opcode, mask, op2, op1.reg)
		}
	}

	private fun encode1Rel832(rel8Opcode: Int, rel32Opcode: Int, op1: OpNode) {
		val imm = resolveImm(op1)

		fun rel8() = byte(rel8Opcode).rel(imm, BYTE)
		fun rel32() = writer.varLengthInt(rel32Opcode).rel(imm, DWORD)

		when(op1.width) {
			null -> when {
				imm.hasReloc -> rel32()
				imm.isImm8   -> rel8()
				else         -> rel32()
			}
			BYTE  -> rel8()
			DWORD -> rel32()
			else  -> invalid()
		}
	}



	/*
	Assembly
	 */



	fun assembleDebug(node: InsNode): Pair<Int, Int> {
		val start = writer.pos
		assemble(node)
		return Pair(start, writer.pos - start)
	}



	private fun assemble(node: InsNode) {
		currentIns = node

		if(node.size == 0)
			assemble0(node)
		else
			assemblers[node.mnemonic.ordinal](node)

		currentIns = null
	}



	private fun assemble0(node: InsNode) {
		when(node.mnemonic) {
			Mnemonic.TILERELEASE -> writer.i40(0xC04978E2C4)
			Mnemonic.RETURN -> encodeRETURN()
			else -> {
				val opcode = Encs.ZERO_OP_OPCODES[node.mnemonic.ordinal]
				if(opcode == 0) invalid()
				writer.varLengthInt(opcode)
			}
		}
	}



	private val InsNode.single get() = if(size != 1) invalid() else op1

	private fun populateAssemblers() {
		operator fun Mnemonic.plusAssign(assembler: (InsNode) -> Unit) = assemblers.set(ordinal, assembler)

		Mnemonic.DLLCALL += ::encodeDLLCALL
		Mnemonic.PUSH    += ::encodePUSH
		Mnemonic.POP     += ::encodePOP
		Mnemonic.IN      += ::encodeIN
		Mnemonic.OUT     += ::encodeOUT
		Mnemonic.MOV     += ::encodeMOV
		Mnemonic.BSWAP   += ::encodeBSWAP
		Mnemonic.XCHG    += ::encodeXCHG
		Mnemonic.TEST    += ::encodeTEST
		Mnemonic.IMUL    += ::encodeIMUL
		Mnemonic.PUSHW   += ::encodePUSHW
		Mnemonic.POPW    += ::encodePOPW
		Mnemonic.CALL    += ::encodeCALL
		Mnemonic.JMP     += ::encodeJMP
		Mnemonic.LEA     += ::encodeLEA
		Mnemonic.ENTER   += ::encodeENTER
		Mnemonic.FSTSW   += ::encodeFSTSW
		Mnemonic.FNSTSW  += ::encodeFSTSW
		Mnemonic.HRESET  += ::encodeHRESET
		Mnemonic.JO      += { encodeJCC(0x70, 0x800F, it) }
		Mnemonic.JNO     += { encodeJCC(0x71, 0x810F, it) }
		Mnemonic.JB      += { encodeJCC(0x72, 0x820F, it) }
		Mnemonic.JNAE    += { encodeJCC(0x72, 0x820F, it) }
		Mnemonic.JC      += { encodeJCC(0x72, 0x820F, it) }
		Mnemonic.JNB     += { encodeJCC(0x73, 0x830F, it) }
		Mnemonic.JAE     += { encodeJCC(0x73, 0x830F, it) }
		Mnemonic.JNC     += { encodeJCC(0x73, 0x830F, it) }
		Mnemonic.JZ      += { encodeJCC(0x74, 0x840F, it) }
		Mnemonic.JE      += { encodeJCC(0x74, 0x840F, it) }
		Mnemonic.JNZ     += { encodeJCC(0x75, 0x850F, it) }
		Mnemonic.JNE     += { encodeJCC(0x75, 0x850F, it) }
		Mnemonic.JBE     += { encodeJCC(0x76, 0x860F, it) }
		Mnemonic.JNA     += { encodeJCC(0x76, 0x860F, it) }
		Mnemonic.JNBE    += { encodeJCC(0x77, 0x870F, it) }
		Mnemonic.JA      += { encodeJCC(0x77, 0x870F, it) }
		Mnemonic.JS      += { encodeJCC(0x78, 0x880F, it) }
		Mnemonic.JNS     += { encodeJCC(0x79, 0x890F, it) }
		Mnemonic.JP      += { encodeJCC(0x7A, 0x8A0F, it) }
		Mnemonic.JPE     += { encodeJCC(0x7A, 0x8A0F, it) }
		Mnemonic.JNP     += { encodeJCC(0x7B, 0x8B0F, it) }
		Mnemonic.JPO     += { encodeJCC(0x7B, 0x8B0F, it) }
		Mnemonic.JL      += { encodeJCC(0x7C, 0x8C0F, it) }
		Mnemonic.JNGE    += { encodeJCC(0x7C, 0x8C0F, it) }
		Mnemonic.JNL     += { encodeJCC(0x7D, 0x8D0F, it) }
		Mnemonic.JGE     += { encodeJCC(0x7D, 0x8D0F, it) }
		Mnemonic.JLE     += { encodeJCC(0x7E, 0x8E0F, it) }
		Mnemonic.JNG     += { encodeJCC(0x7E, 0x8E0F, it) }
		Mnemonic.JNLE    += { encodeJCC(0x7F, 0x8F0F, it) }
		Mnemonic.JG      += { encodeJCC(0x7F, 0x8F0F, it) }
		Mnemonic.RET     += { encode1I(0xC2, WORD, it) }
		Mnemonic.RETF    += { encode1I(0xCA, WORD, it) }
		Mnemonic.RETW    += { encode1I(0xC266, WORD, it) }
		Mnemonic.RETFQ   += { encode1I(0xCA48, WORD, it) }
		Mnemonic.INT     += { encode1I(0xCD, BYTE, it) }
		Mnemonic.LOOP    += { encode1REL(0xE2, BYTE, it) }
		Mnemonic.LOOPE   += { encode1REL(0xE1, BYTE, it) }
		Mnemonic.LOOPZ   += { encode1REL(0xE1, BYTE, it) }
		Mnemonic.LOOPNE  += { encode1REL(0xE0, BYTE, it) }
		Mnemonic.LOOPNZ  += { encode1REL(0xE0, BYTE, it) }
		Mnemonic.JECXZ   += { encode1REL(0xE367, BYTE, it) }
		Mnemonic.JRCXZ   += { encode1REL(0xE3, BYTE, it) }
		Mnemonic.XBEGIN  += { encode1REL(0xF8C7, DWORD, it) }
		Mnemonic.XABORT  += { encode1I(0xF8C6, BYTE, it) }
		Mnemonic.JMPF    += { encode1M(0xFF, 0b1110, 5, it.single, 0) }
		Mnemonic.CALLF   += { encode1M(0xFF, 0b1110, 3, it.single, 0) }
		Mnemonic.SHLD    += { encodeSHLD(0xA40F, it) }
		Mnemonic.SHRD    += { encodeSHLD(0xAC0F, it) }
		Mnemonic.ADD     += { encodeADD(0x00, 0, it) }
		Mnemonic.OR      += { encodeADD(0x08, 1, it) }
		Mnemonic.ADC     += { encodeADD(0x10, 2, it) }
		Mnemonic.SBB     += { encodeADD(0x18, 3, it) }
		Mnemonic.AND     += { encodeADD(0x20, 4, it) }
		Mnemonic.SUB     += { encodeADD(0x28, 5, it) }
		Mnemonic.XOR     += { encodeADD(0x30, 6, it) }
		Mnemonic.CMP     += { encodeADD(0x38, 7, it) }
		Mnemonic.ROL     += { encodeROL(0, it) }
		Mnemonic.ROR     += { encodeROL(1, it) }
		Mnemonic.RCL     += { encodeROL(2, it) }
		Mnemonic.RCR     += { encodeROL(3, it) }
		Mnemonic.SAL     += { encodeROL(4, it) }
		Mnemonic.SHL     += { encodeROL(4, it) }
		Mnemonic.SHR     += { encodeROL(5, it) }
		Mnemonic.SAR     += { encodeROL(7, it) }
	}



	/*
	Manual encoding
	 */



	private fun encodeHRESET(node: InsNode) {
		if(node.size != 1 && (node.size == 2 && node.r2 != Reg.EAX)) invalid()
		if(node.op1.type != OpType.IMM) invalid()
		word(0x0FF3)
		i24(0xC0F03A)
		imm(node.op1, BYTE)
	}

	private fun encodeFSTSW(node: InsNode) {
		if(node.mnemonic == Mnemonic.FSTSW) byte(0x9B)
		val op1 = node.single
		when {
			op1.reg == Reg.AX -> word(0xE0DF)
			op1.isMem         -> encode1MSingle(0xDD, WORD, 7, op1)
			else              -> invalid()
		}
	}

	private fun encodeSHLD(opcode: Int, node: InsNode) {
		if(node.size != 3) invalid()
		when {
			node.op3.reg == Reg.CL      -> encode2RMR(opcode + (1 shl 8), 0b1110, node.op1, node.op2.reg, 0)
			node.op3.type == OpType.IMM -> encode2RMR(opcode, 0b1110, node.op1, node.op2.reg, 1).imm(node.op3, BYTE)
			else                        -> invalid()
		}
	}

	private fun encodeADD(start: Int, ext: Int, node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2
		if(node.op2.isImm) {
			val imm = resolveImm(op2.node)
			fun ai() = encode1I(start + 4, op2, op1.reg.width)
			fun i8() = encode1RM(0x83, 0b1110, ext, op1, 1).imm(imm, BYTE)
			fun i() = encode1RM(0x80, 0b1111, ext, op1, op1.immWidth().bytes).imm(imm, op1.immWidth())
			when {
				op1.reg == Reg.AL -> ai()
				op1.width == BYTE -> i()
				op2.width == BYTE -> i8()
				op2.width != null -> i()
				imm.hasReloc      -> i()
				imm.isImm8        -> i8()
				op1.reg.isA       -> ai()
				else              -> i()
			}
		} else if(op2.isMem) {
			encode2RM(start + 2, 0b1111, op1.reg, op2, 0)
		} else if(op1.isMem) {
			encode2RM(start, 0b1111, op2.reg, op1, 0)
		} else {
			encode2RR(start, 0b1111, op2.reg, op1.reg)
		}
	}

	private fun encodeROL(ext: Int, node: InsNode) {
		if(node.size != 2) invalid()
		if(node.r2 == Reg.CL) {
			encode1RM(0xD2, 0b1111, ext, node.op1, 0)
		} else if(node.op2.isImm) {
			val imm = resolveImm(node.op2)
			if(!imm.hasReloc && imm.disp == 1L)
				encode1RM(0xD0, 0b1111, ext, node.op1, 0)
			else
				encode1RM(0xC0, 0b1111, ext, node.op1, 1).imm(imm, BYTE)
		} else {
			invalid()
		}
	}

	private fun encodeTEST(node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2

		when(op2.type) {
			OpType.MEM -> encode2RMR(0x84, 0b1111, op2, op1.reg, 0)
			OpType.IMM -> if(op1.reg.isA)
				encode1I(0xA8, op2, op1.reg.width)
			else
				encode1RM(0xF6, 0b1111, 0, op1, op1.immWidth().bytes).imm(op2, op1.immWidth())
			else -> encode2RMR(0x84, 0b1111, op1, op2.reg, 0)
		}
	}

	private fun encodeIMUL(node: InsNode) {
		val op1 = node.op1
		val op2 = node.op2
		val op3 = node.op3

		when(node.size) {
			1 -> encode1RM(0xF6, 0b1111, 5, op1, 0)
			2 -> encode2RRM(0xAF0F, 0b1110, op1.reg, op2, 0)
			3 -> {
				val imm = resolveImm(op3)

				if(op3.width == BYTE || (op3.width == null && !imm.hasReloc && imm.disp.isImm8)) {
					encode2RRM(0x6B, 0b1110, op1.reg, op2, 1).imm(imm, BYTE)
				} else {
					val width = if(op1.width == QWORD) DWORD else op1.reg.width
					encode2RRM(0x69, 0b1110, op1.reg, op2, width.bytes).imm(imm, width)
				}
			}
			else -> invalid()
		}
	}

	private fun encodeIN(node: InsNode) {
		if(node.size != 2) invalid()
		when {
			node.r2 == Reg.DX -> when(node.r1) {
				Reg.AL  -> byte(0xEC)
				Reg.AX  -> word(0xED66)
				Reg.EAX -> byte(0xED)
				else    -> invalid()
			}
			node.op2.isImm -> when(node.r1) {
				Reg.AL  -> byte(0xE4).imm(node.op2, BYTE)
				Reg.AX  -> word(0xE566).imm(node.op2, BYTE)
				Reg.EAX -> byte(0xE5).imm(node.op2, BYTE)
				else    -> invalid()
			}
			else -> invalid()
		}
	}

	private fun encodeOUT(node: InsNode) {
		if(node.size != 2) invalid()
		when {
			node.r1 == Reg.DX -> when(node.r2) {
				Reg.AL  -> byte(0xEE)
				Reg.AX  -> word(0xEF66)
				Reg.EAX -> byte(0xEF)
				else    -> invalid()
			}
			node.op1.isImm -> when(node.r2) {
				Reg.AL  -> byte(0xE6).imm(node.op1, BYTE)
				Reg.AX  -> word(0xE766).imm(node.op1, BYTE)
				Reg.EAX -> byte(0xE7).imm(node.op1, BYTE)
				else    -> invalid()
			}
			else -> invalid()
		}
	}

	private fun encodeLEA(node: InsNode) {
		if(node.size != 2) invalid()
		if(!node.op2.isMem) invalid()
		encode2RM(0x8D, 0b1110, node.op1.reg, node.op2, 0)
	}

	private fun encodeENTER(node: InsNode) {
		if(node.size != 2) invalid()
		if(node.op1.type != OpType.IMM || node.op2.type != OpType.IMM) invalid()
		val imm1 = resolveImm(node.op1)
		val imm2 = resolveImm(node.op2)
		if(imm1.hasReloc || imm2.hasReloc) invalid()
		if(node.mnemonic == Mnemonic.ENTERW) word(0xC877) else byte(0xC8)
		imm(imm1, WORD)
		imm(imm2, BYTE)
	}

	private fun encodeXCHG(node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2

		when {
			op1.reg == Reg.EAX && op2.reg == Reg.EAX -> word(0xC087)
			op1.isMem         -> encode2RM(0x86, 0b1111, op2.reg, op1, 0)
			op2.isMem         -> encode2RM(0x86, 0b1111, op1.reg, op2, 0)
			op1.width == BYTE -> encode2RR(0x86, 0b1111, op1.reg, op2.reg)
			op1.reg.isA       -> encode1O(0x90, 0b1110, op2.reg)
			op2.reg.isA       -> encode1O(0x90, 0b1110, op1.reg)
			else              -> encode2RR(0x86, 0b1111, op1.reg, op2.reg)
		}
	}

	private fun encodeBSWAP(node: InsNode) {
		if(node.size != 1) invalid()
		val op1 = node.op1.reg
		when(op1.type) {
			OpType.R32 -> writeRex(0, 0, 0, op1.rex)
			OpType.R64 -> writeRex(1, 0, 0, op1.rex)
			else       -> invalid()
		}
		word(0xC80F + (op1.value shl 8))
	}

	private fun encodeMOVRR(opcode: Int, op1: Reg, op2: Reg) {
		if(op2.type != OpType.R64) invalid()
		writeRex(0, op1.rex, 0, op2.rex)
		word(opcode)
		byte(0b11_000_000 or (op1.value shl 3) or (op2.value))
	}

	private fun encodeMOVMSEG(opcode: Int, op1: Reg, op2: OpNode) {
		val mem = resolveMem(op2)
		writeRex(if(op2.width == QWORD) 1 else 0, 0, mem.rexX, mem.rexB)
		byte(opcode)
		mem.write(op1.value, 0)
	}

	private fun encodeMOVRSEG(opcode: Int, op1: Reg, op2: Reg) {
		when(op2.type) {
			OpType.R16 -> { byte(0x66); writeRex(0, op1.rex, 0, op2.rex) }
			OpType.R32 -> writeRex(0, op1.rex, 0, op2.rex)
			OpType.R64 -> writeRex(1, op1.rex, 0, op2.rex)
			else       -> invalid()
		}
		byte(opcode)
		byte(0b11_000_000 or (op1.value shl 3) or (op2.value))
	}

	private fun encodeMOV(node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2

		when(op1.type) {
			OpType.MEM -> when(op2.type) {
				OpType.IMM -> encode1M(0xC6, 0b1111, 0, op1, op1.immWidth().bytes).imm(op2, op1.immWidth())
				else -> when(op2.type) {
					OpType.SEG -> encodeMOVMSEG(0x8C, op2.reg, op1)
					else       -> encode2RM(0x88, 0b1111, op2.reg, op1, 0)
				}
			}
			else -> when(op2.type) {
				OpType.MEM -> when(op1.type) {
					OpType.SEG -> encodeMOVMSEG(0x8E, op1.reg, op2)
					else        -> encode2RM(0x8A, 0b1111, op1.reg, op2, 0)
				}
				OpType.IMM -> {
					val imm = resolveImm(op2)
					// MOV R64, I64 -> MOV R32, I32
					if(imm.width == null && op1.reg.type == OpType.R64 && !imm.hasReloc && imm.isImm32)
						encode1O(0xB0, 0b1111, Reg.r32(op1.reg.index)).imm(op2, DWORD)
					else
						encode1O(0xB0, 0b1111, op1.reg).imm(op2, op1.reg.width)
				}
				else -> when {
					op1.reg.isR && op2.reg.isR -> encode2RR(0x88, 0b1111, op2.reg, op1.reg)
					op1.type == OpType.CR      -> encodeMOVRR(0x220F, op1.reg, op2.reg)
					op2.type == OpType.CR      -> encodeMOVRR(0x200F, op2.reg, op1.reg)
					op1.type == OpType.DR      -> encodeMOVRR(0x230F, op1.reg, op2.reg)
					op2.type == OpType.DR      -> encodeMOVRR(0x210F, op2.reg, op1.reg)
					op1.type == OpType.SEG     -> encodeMOVRSEG(0x8E, op1.reg, op2.reg)
					op2.type == OpType.SEG     -> encodeMOVRSEG(0x8C, op2.reg, op1.reg)
					else -> invalid()
				}
			}
		}
	}

	private fun encodeCALL(node: InsNode) {
		when(node.single.type) {
			OpType.MEM -> encode1MSingle(0xFF, QWORD, 2, node.op1)
			OpType.IMM -> byte(0xE8).rel(node.op1, DWORD)
			else       -> encode1R(0xFF, 0b1000, 2, node.r1)
		}
	}

	private fun encodeJMP(node: InsNode) {
		when(node.single.type) {
			OpType.MEM -> encode1MSingle(0xFF, QWORD, 4, node.op1)
			OpType.IMM -> encode1Rel832(0xEB, 0xE9, node.op1)
			else       -> encode1R(0XFF, 0b1000, 4, node.r1)
		}
	}

	private fun encodeJCC(rel8Opcode: Int, rel32Opcode: Int, node: InsNode) {
		if(node.size != 1) invalid()
		encode1Rel832(rel8Opcode, rel32Opcode, node.op1)
	}

	private fun encodePUSH(node: InsNode) {
		when(node.single.type) {
			OpType.R16 -> encode1O(0x50, 0b1010, node.r1)
			OpType.R64 -> encode1O(0x50, 0b1010, node.r1)
			OpType.SEG -> when(node.r1) {
				Reg.FS -> word(0xA00F)
				Reg.GS -> word(0xA80F)
				else   -> invalid()
			}
			OpType.MEM -> encode1M(0xFF, 0b1010, 6, node.op1, 0)
			OpType.IMM -> {
				val imm = resolveImm(node.op1)

				fun i32() = byte(0x68).imm(imm, DWORD)
				fun i16() = word(0x6866).imm(imm, WORD)
				fun i8() = byte(0x6A).imm(imm, BYTE)

				when(imm.width) {
					null -> when {
						imm.hasReloc -> i32()
						imm.isImm8   -> i8()
						imm.isImm16  -> i16()
						else         -> i32()
					}
					BYTE  -> i8()
					WORD  -> i16()
					DWORD -> i32()
					else  -> invalid()
				}
			}
			else -> invalid()
		}
	}

	private fun encodePOP(node: InsNode) {
		when(node.single.type) {
			OpType.MEM -> encode1M(0x8F, 0b1010, 0, node.op1, 0)
			OpType.SEG -> when(node.r1) {
				Reg.FS -> word(0xA10F)
				Reg.GS -> word(0xA90F)
				else   -> invalid()
			}
			else -> encode1O(0x58, 0b1010, node.r1)
		}
	}

	private fun encodePUSHW(node: InsNode) {
		when(node.single.reg) {
			Reg.FS -> writer.i24(0xA80F66)
			Reg.GS -> writer.i32(0xA80F66)
			else   -> invalid()
		}
	}

	private fun encodePOPW(node: InsNode) {
		when(node.single.reg) {
			Reg.FS -> writer.i24(0xA10F66)
			Reg.GS -> writer.i32(0xA10F66)
			else   -> invalid()
		}
	}

	private fun encodeDLLCALL(node: InsNode) {
		val op1 = node.single
		val nameNode = op1.node as? NameNode ?: invalid()
		nameNode.symbol = context.getDllImport(nameNode.name)
		if(nameNode.symbol == null) invalid("Unrecognised dll import: ${nameNode.name}")
		encode1M(0xFF, 0b1000, 2, OpNode.mem(QWORD, nameNode), 0)
	}

	private fun encodeRETURN() {
		if(epilogueWriter.isEmpty) invalid("Return invalid here")
		writer.bytes(epilogueWriter)
	}



	/*
	Auto encoding
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
				(r shl 15) or (x shl 14) or (b shl 13) or (escape.avxValue shl 8) or
				(vexw.value shl 23) or (vvvv shl 19) or (vexl.value shl 18) or (prefix.avxValue shl 16) or
				(opcode shl 24)
			)
		else
			i24(
				(0xC5 shl 0) or
				(r shl 15) or (vvvv shl 11) or (vexl.value shl 10) or (prefix.avxValue shl 8) or
				(opcode shl 16)
			)
	}



	private fun getEnc(mnemonic: Mnemonic, ops: AutoOps): NasmEnc? {
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

		val simdOps = AutoOps(
			node.op1.type.ordinal,
			node.op2.type.ordinal,
			node.op3.type.ordinal,
			node.op4.type.ordinal,
			ops.firstOrNull { it.isMem }?.width?.let { it.ordinal + 1 } ?: 0,
			ops.firstOrNull { it.isMem }?.let { resolveMem(it).vsib } ?: 0
		)

		return getEnc(node.mnemonic, simdOps)
	}



	private fun assembleAuto(node: InsNode) {
		if(node.high() == 1)
			invalid("EVEX not currently supported")

		if(node.op1.type == OpType.ST) {
			val encs = EncGen.map[node.mnemonic] ?: invalid()
			val r1 = node.r1
			val r2 = node.r2
			if(node.size == 1) {
				val enc = encs.firstOrNull { it.fpuOps == 1 } ?: invalid()
				word(enc.opcode + (r1.value shl 8))
			} else if(node.size == 2) {
				if(r2.type != OpType.ST) invalid()
				if(r2 == Reg.ST0) {
					val enc = encs.firstOrNull { it.fpuOps == 2 }
						?: encs.firstOrNull { it.fpuOps == 1 && r1 == Reg.ST0 } ?: invalid()
					word(enc.opcode + (r1.value shl 8))
				} else if(r1 == Reg.ST0) {
					val enc = encs.firstOrNull { it.fpuOps == 1 } ?: invalid()
					word(enc.opcode + (r2.value shl 8))
				} else invalid()
			} else invalid()
			return
		}
		
		var mem = Mem.NULL
		var imm = Mem.NULL
		
		fun type(node: OpNode) = when(node.type) {
			OpType.MEM -> { mem = resolveMem(node); OpType.MEM.ordinal }
			OpType.IMM -> { imm = resolveImm(node); OpType.IMM.ordinal }
			else       -> node.type.ordinal
		}
		
		val ops = AutoOps(
			type(node.op1),
			type(node.op2),
			type(node.op3),
			type(node.op4),
			mem.width?.let { it.ordinal + 1 } ?: 0,
			mem.vsib
		)

		val immLength = if(imm != Mem.NULL) 1 else 0
		
		val enc = getEnc(node.mnemonic, ops) ?: invalid("No encodings found")

		var r: Reg
		val m: Reg
		val v: Reg

		when(enc.simdOpEnc) {
			OpEnc.RMV -> { r = node.op1.reg; m = node.op2.reg; v = node.op3.reg }
			OpEnc.RVM -> { r = node.op1.reg; v = node.op2.reg; m = node.op3.reg }
			OpEnc.MRV -> { m = node.op1.reg; r = node.op2.reg; v = node.op3.reg }
			OpEnc.MVR -> { m = node.op1.reg; v = node.op2.reg; r = node.op3.reg }
			OpEnc.VMR -> { v = node.op1.reg; m = node.op2.reg; r = node.op3.reg }
		}

		if(enc.hasExt)
			r = Reg.r32(enc.ext)

		if(enc.avx) {
			if(mem != Mem.NULL) {
				if(mem.a32) byte(0x67)
				enc.writeVex(r.vexRex, mem.vexX, mem.vexB, v.vValue)
				mem.write(r.value, immLength)
			} else {
				enc.writeVex(r.vexRex, 1, m.vexRex, v.vValue)
				byte(0b11_000_000 or (r.value shl 3) or (m.value))
			}
		} else {
			if(enc.o16 == 1) byte(0x66)
			if(enc.a32 == 1) byte(0x67)
			if(mem != Mem.NULL) {
				if(mem.a32) byte(0x67)
				if(enc.prefix != Prefix.NONE) byte(enc.prefix.value)
				writeRex(enc.rw, r.rex, mem.rexX, mem.rexB, r.rex8 or m.rex8, r.noRex or m.noRex)
				enc.writeSimdOpcode()
				mem.write(r.value, immLength)
			} else {
				if(enc.prefix != Prefix.NONE) byte(enc.prefix.value)
				writeRex(enc.rw, r.rex, 0, m.rex, r.rex8 or m.rex8, r.noRex or m.noRex)
				enc.writeSimdOpcode()
				byte(0b11_000_000 or (r.value shl 3) or (m.value))
			}
		}

		when {
			node.op4.reg != Reg.NONE -> byte(node.op4.reg.index shl 4)
			imm != Mem.NULL -> imm(imm, BYTE)
			enc.pseudo >= 0 -> byte(enc.pseudo)
		}
	}


}