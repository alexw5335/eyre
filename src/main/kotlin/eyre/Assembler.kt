package eyre

import eyre.Width.*
import eyre.OpNodeType.*
import eyre.gen.EncGen
import eyre.gen.NasmEnc

class Assembler(private val context: CompilerContext) {


	private var writer = context.textWriter

	private var section = Section.TEXT

	private var currentIns: InsNode? = null

	private val assemblers = Array<(InsNode) -> Unit>(Mnemonic.entries.size) { ::assembleAuto }



	init {
		populateAssemblers()
	}



	fun assemble() {
		for(srcFile in context.srcFiles) {
			for(node in srcFile.nodes) {
				when(node) {
					is InsNode       -> assemble(node)
					is LabelNode     -> handleLabel(node.symbol)
					is ProcNode      -> handleLabel(node.symbol)
					is ScopeEndNode  -> handleScopeEnd(node)
					else             -> { }
				}
			}
		}
	}



	private fun invalid(string: String = "Assembler error"): Nothing {
		error("$string: ${currentIns?.printString}")
	}



	/*
	Nodes
	 */



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
	Resolution
	 */



	private fun addLinkReloc(width: Width, node: AstNode) =
		context.relocs.add(Reloc(writer.pos, section, width, node, 0, RelocType.LINK))

	private fun addRelReloc(width: Width, node: AstNode, offset: Int) =
		context.relocs.add(Reloc(writer.pos, section, width, node, offset, RelocType.RIP))

	private fun addAbsReloc(node: AstNode) {
		context.relocs.add(Reloc(writer.pos, section, QWORD, node, 0, RelocType.ABS))
		context.absRelocCount++
	}



	private fun Mem.checkReg(reg: Reg) {
		when(reg.type) {
			RegType.R32 -> if(aso == 2) invalid() else aso = 1
			RegType.R64 -> if(aso == 1) invalid() else aso = 2
			RegType.X   -> vsib = 1
			RegType.Y   -> vsib = 2
			RegType.Z   -> vsib = 3
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
			mem.width = node.width
			mem.disp = resolveRec(node.node, mem, true)
		} else {
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
		if(mem.relocs != 0) addRelReloc(width, mem.node, 0)
		writer.writeWidth(width, mem.disp)
	}



	private fun Any.imm(mem: Mem, width: Width) {
		if(mem.width != null && mem.width != width) invalid()
		if(mem.relocs == 1) {
			if(width != QWORD) invalid()
			addAbsReloc(mem.node)
			writer.advance(8)
		} else if(mem.relocs > 1) {
			addLinkReloc(width, mem.node)
			writer.advance(width.bytes)
		} else if(!writer.writeWidth(width, mem.disp)) {
			invalid()
		}
	}



	private fun Any.rel(op: OpNode, width: Width) =
		rel(resolveMem(op), width)



	private fun Any.imm(op: OpNode, width: Width) =
		imm(resolveMem(op), width)



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

	private fun checkWidth(mask: Int, width: Width) {
		if((1 shl width.ordinal) and mask == 0) invalid()
	}

	private fun writeO16(mask: Int, width: Width) {
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
	private fun rexw(mask: Int, width: Width) =
		(width.bytes shr 3) and (mask shr 2)

	/** Add one if width is not BYTE and if widths has BYTE set */
	private fun writeOpcode(opcode: Int, mask: Int, width: Width) {
		val addition = (mask and 1) and (1 shl width.ordinal).inv()
		if(opcode and 0xFF00 != 0)
			word(opcode + (addition shl 8))
		else
			byte(opcode + addition)
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
	Manual Encoding
	 */



	private fun OpNode.immWidth() = when(width) {
		null  -> invalid()
		QWORD -> DWORD
		else  -> width
	}

	private fun encode1REL(opcode: Int, width: Width, node: InsNode) {
		if(node.size != 1) invalid()
		if(node.op1.type != IMM) invalid()
		writer.varLengthInt(opcode)
		rel(node.op1, width)
	}

	private fun encode1I(opcode: Int, width: Width, node: InsNode) {
		if(node.size != 1) invalid()
		if(node.op1.type != IMM) invalid()
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

	private fun encode1M(opcode: Int, mask: Int, ext: Int, op1: OpNode, immLength: Int) {
		val mem = resolveMem(op1)
		if(mask.countOneBits() == 1) {
			if(op1.width != null) checkWidth(mask, op1.width)
			writeA32(mem)
			writeRex(0, 0, mem.rexX, mem.rexB)
			writer.varLengthInt(opcode)
			mem.write(ext, immLength)
		} else {
			val width = op1.width ?: invalid()
			checkWidth(mask, width)
			writeO16(mask, width)
			writeA32(mem)
			writeRex(rexw(mask, width), 0, mem.rexX, mem.rexB)
			writeOpcode(opcode, mask, width)
			mem.write(ext, immLength)
		}
	}

	private fun encode1R(opcode: Int, mask: Int, ext: Int, op1: Reg) {
		val width = op1.width
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(rexw(mask, width), 0, 0, op1.rex, op1.rex8, op1.noRex)
		writeOpcode(opcode, mask, width)
		byte(0b11000000 or (ext shl 3) or op1.value)
	}

	private fun encode1O(opcode: Int, mask: Int, op1: Reg) {
		val width = op1.width
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(rexw(mask, width), 0, 0, op1.rex, op1.rex8, op1.noRex)
		byte(opcode + (((mask and 1) and (1 shl width.ordinal).inv()) shl 3) + op1.value)
	}

	private fun encode2RM(opcode: Int, mask: Int, op1: Reg, op2: OpNode, immLength: Int) {
		val width = op1.width
		if(op2.width != null && op2.width.ordinal != op1.type.ordinal) invalid()
		val mem = resolveMem(op2)
		checkWidth(mask, width)
		writeO16(mask, width)
		writeA32(mem)
		writeRex(rexw(mask, width), op1.rex, mem.rexX, mem.rexB, op1.rex8, op1.noRex)
		writeOpcode(opcode, mask, width)
		mem.write(op1.value, immLength)
	}

	private fun encode2RR(opcode: Int, mask: Int, op1: Reg, op2: Reg) {
		val width = op1.width
		if(op1.type != op2.type) invalid()
		checkWidth(mask, width)
		writeO16(mask, width)
		writeRex(rexw(mask, width), op1.rex, 0, op2.rex, op1.rex8 or op2.rex8, op1.noRex or op2.noRex)
		writeOpcode(opcode, mask, width)
		writeModRM(0b11, op1.value, op2.value)
	}

	private fun encode1RM(opcode: Int, mask: Int, ext: Int, op1: OpNode, immLength: Int) {
		when(op1.type) {
			REG -> encode1R(opcode, mask, ext, op1.reg)
			MEM -> encode1M(opcode, mask, ext, op1, immLength)
			IMM -> invalid()
		}
	}

	private fun encode2RRM(opcode: Int, mask: Int, op1: Reg, op2: OpNode, immLength: Int) {
		when(op2.type) {
			REG -> encode2RR(opcode, mask, op1, op2.reg)
			MEM -> encode2RM(opcode, mask, op1, op2, immLength)
			else -> invalid()
		}
	}

	private fun encode2RMR(opcode: Int, mask: Int, op1: OpNode, op2: Reg, immLength: Int) {
		when(op1.type) {
			REG -> encode2RR(opcode, mask, op2, op1.reg)
			MEM -> encode2RM(opcode, mask, op2, op1, immLength)
			else -> invalid()
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
			Mnemonic.RETURN -> byte(0xC3)
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

		Mnemonic.PUSH += ::encodePUSH
		Mnemonic.POP += ::encodePOP
		Mnemonic.IN += ::encodeIN
		Mnemonic.OUT += ::encodeOUT
		Mnemonic.MOV += ::encodeMOV
		Mnemonic.BSWAP += ::encodeBSWAP
		Mnemonic.XCHG += ::encodeXCHG
		Mnemonic.TEST += ::encodeTEST
		Mnemonic.IMUL += ::encodeIMUL
		Mnemonic.PUSHW += ::encodePUSHW
		Mnemonic.POPW += ::encodePOPW
		Mnemonic.CALL += ::encodeCALL
		Mnemonic.JMP += ::encodeJMP
		Mnemonic.LEA += ::encodeLEA
		Mnemonic.ENTER += ::encodeENTER
		Mnemonic.JO += { encodeJCC(0x70, 0x800F, it) }
		Mnemonic.JNO += { encodeJCC(0x71, 0x810F, it) }
		Mnemonic.JB += { encodeJCC(0x72, 0x820F, it) }
		Mnemonic.JNAE += { encodeJCC(0x72, 0x820F, it) }
		Mnemonic.JC += { encodeJCC(0x72, 0x820F, it) }
		Mnemonic.JNB += { encodeJCC(0x73, 0x830F, it) }
		Mnemonic.JAE += { encodeJCC(0x73, 0x830F, it) }
		Mnemonic.JNC += { encodeJCC(0x73, 0x830F, it) }
		Mnemonic.JZ += { encodeJCC(0x74, 0x840F, it) }
		Mnemonic.JE += { encodeJCC(0x74, 0x840F, it) }
		Mnemonic.JNZ += { encodeJCC(0x75, 0x850F, it) }
		Mnemonic.JNE += { encodeJCC(0x75, 0x850F, it) }
		Mnemonic.JBE += { encodeJCC(0x76, 0x860F, it) }
		Mnemonic.JNA += { encodeJCC(0x76, 0x860F, it) }
		Mnemonic.JNBE += { encodeJCC(0x77, 0x870F, it) }
		Mnemonic.JA += { encodeJCC(0x77, 0x870F, it) }
		Mnemonic.JS += { encodeJCC(0x78, 0x880F, it) }
		Mnemonic.JNS += { encodeJCC(0x79, 0x890F, it) }
		Mnemonic.JP += { encodeJCC(0x7A, 0x8A0F, it) }
		Mnemonic.JPE += { encodeJCC(0x7A, 0x8A0F, it) }
		Mnemonic.JNP += { encodeJCC(0x7B, 0x8B0F, it) }
		Mnemonic.JPO += { encodeJCC(0x7B, 0x8B0F, it) }
		Mnemonic.JL += { encodeJCC(0x7C, 0x8C0F, it) }
		Mnemonic.JNGE += { encodeJCC(0x7C, 0x8C0F, it) }
		Mnemonic.JNL += { encodeJCC(0x7D, 0x8D0F, it) }
		Mnemonic.JGE += { encodeJCC(0x7D, 0x8D0F, it) }
		Mnemonic.JLE += { encodeJCC(0x7E, 0x8E0F, it) }
		Mnemonic.JNG += { encodeJCC(0x7E, 0x8E0F, it) }
		Mnemonic.JNLE += { encodeJCC(0x7F, 0x8F0F, it) }
		Mnemonic.JG += { encodeJCC(0x7F, 0x8F0F, it) }
		Mnemonic.RET += { encode1I(0xC2, WORD, it) }
		Mnemonic.RETF += { encode1I(0xCA, WORD, it) }
		Mnemonic.RETW += { encode1I(0xC266, WORD, it) }
		Mnemonic.RETFQ += { encode1I(0xCA48, WORD, it) }
		Mnemonic.INT += { encode1I(0xCD, BYTE, it) }
		Mnemonic.LOOP += { encode1REL(0xE2, BYTE, it) }
		Mnemonic.LOOPE += { encode1REL(0xE1, BYTE, it) }
		Mnemonic.LOOPZ += { encode1REL(0xE1, BYTE, it) }
		Mnemonic.LOOPNE += { encode1REL(0xE0, BYTE, it) }
		Mnemonic.LOOPNZ += { encode1REL(0xE0, BYTE, it) }
		Mnemonic.JECXZ += { encode1REL(0xE367, BYTE, it) }
		Mnemonic.JRCXZ += { encode1REL(0xE3, BYTE, it) }
		Mnemonic.XBEGIN += { encode1REL(0xF8C7, DWORD, it) }
		Mnemonic.XABORT += { encode1I(0xF8C6, BYTE, it) }
		Mnemonic.HRESET += { word(0x0FF3); encode1I(0xC0F03A, BYTE, it) }
		Mnemonic.JMPF += { encode1M(0xFF, 0b1110, 5, it.single, 0) }
		Mnemonic.CALLF += { encode1M(0xFF, 0b1110, 3, it.single, 0) }
		Mnemonic.SHLD += { encodeSHLD(0xA40F, it) }
		Mnemonic.SHRD += { encodeSHLD(0xAC0F, it) }
		Mnemonic.ADD += { encodeADD(0x00, 0, it) }
		Mnemonic.OR  += { encodeADD(0x08, 1, it) }
		Mnemonic.ADC += { encodeADD(0x10, 2, it) }
		Mnemonic.SBB += { encodeADD(0x18, 3, it) }
		Mnemonic.AND += { encodeADD(0x20, 4, it) }
		Mnemonic.SUB += { encodeADD(0x28, 5, it) }
		Mnemonic.XOR += { encodeADD(0x30, 6, it) }
		Mnemonic.CMP += { encodeADD(0x38, 7, it) }
		Mnemonic.ROL += { encodeROL(0, it) }
		Mnemonic.ROR += { encodeROL(1, it) }
		Mnemonic.RCL += { encodeROL(2, it) }
		Mnemonic.RCR += { encodeROL(3, it) }
		Mnemonic.SAL += { encodeROL(4, it) }
		Mnemonic.SHL += { encodeROL(4, it) }
		Mnemonic.SHR += { encodeROL(5, it) }
		Mnemonic.SAR += { encodeROL(7, it) }
	}



	/*
	Manual encoding
	 */



	private fun encodeSHLD(opcode: Int, node: InsNode) {
		if(node.size != 3) invalid()
		when {
			node.op3.reg == Reg.CL -> encode2RMR(opcode + (1 shl 8), 0b1110, node.op1, node.op2.reg, 0)
			node.op3.type == IMM   -> encode2RMR(opcode, 0b1110, node.op1, node.op2.reg, 1).imm(node.op3, BYTE)
			else                   -> invalid()
		}
	}

	private fun encodeADD(start: Int, ext: Int, node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2

		when(op1.type) {
			REG -> when(op2.type) {
				REG -> encode2RR(start, 0b1111, op2.reg, op1.reg)
				MEM -> encode2RM(start + 2, 0b1111, op1.reg, op2, 0)
				IMM -> {
					val imm = resolveImm(op2.node)

					fun ai() = encode1I(start + 4, op2, op1.reg.width)
					fun i8() = encode1R(0x83, 0b1110, ext, op1.reg).imm(imm, BYTE)
					fun i()  = encode1R(0x80, 0b1111, ext, op1.reg).imm(imm, op1.immWidth())

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
				}
			}
			MEM -> when(op2.type) {
				REG -> encode2RM(start, 0b1111, op2.reg, op1, 0)
				MEM -> invalid()
				IMM -> {
					val imm = resolveImm(op2.node)

					fun i8() = encode1M(0x83, 0b1110, ext, op1, 1).imm(imm, BYTE)
					fun i() = encode1M(0x80, 0b1111, ext, op1, op1.immWidth().bytes).imm(imm, op1.immWidth())

					when {
						op1.width == BYTE -> i()
						op2.width == BYTE -> i8()
						op2.width != null -> i()
						imm.hasReloc      -> i()
						imm.isImm8        -> i8()
						else              -> i()
					}
				}
			}
			IMM -> invalid()
		}
	}

	private fun encodeROL(ext: Int, node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2

		when(op1.type) {
			REG -> when(op2.type) {
				REG -> if(op2.reg != Reg.CL) invalid() else encode1R(0xD2, 0b1111, ext, op1.reg)
				MEM -> invalid()
				IMM -> resolveImm(op2).let { imm ->
					if(!imm.hasReloc && imm.disp == 1L)
						encode1R(0xD0, 0b1111, ext, op1.reg)
					else
						encode1R(0xC0, 0b1111, ext, op1.reg).imm(imm, BYTE)
				}
			}
			MEM -> when(op2.type) {
				REG -> if(op2.reg != Reg.CL) invalid() else encode1M(0xD2, 0b1111, ext, op1, 0)
				MEM -> invalid()
				IMM -> resolveImm(op2).let { imm ->
					if(!imm.hasReloc && imm.disp == 1L)
						encode1M(0xD0, 0b1111, ext, op1, 0)
					else
						encode1M(0xC0, 0b1111, ext, op1, 1).imm(imm, BYTE)
				}
			}
			IMM -> invalid()
		}
	}

	private fun encodeTEST(node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2

		when(op2.type) {
			REG -> encode2RMR(0x84, 0b1111, op1, op2.reg, 0)
			MEM -> encode2RMR(0x84, 0b1111, op2, op1.reg, 0)
			IMM -> if(op1.reg.isA)
				encode1I(0xA8, op2, op1.reg.width)
			else
				encode1RM(0xF6, 0b1111, 0, op1, op1.immWidth().bytes).imm(op2, op1.immWidth())
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

	private fun encodeLEA(node: InsNode) {
		if(node.size != 2) invalid()
		if(!node.op2.isMem) invalid()
		encode2RM(0x8D, 0b1110, node.op1.reg, node.op2, 0)
	}

	private fun encodeENTER(node: InsNode) {
		if(node.size != 2) invalid()
		if(node.op1.type != IMM || node.op2.type != IMM) invalid()
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
			RegType.R32 -> writeRex(0, 0, 0, op1.rex)
			RegType.R64 -> writeRex(1, 0, 0, op1.rex)
			else        -> invalid()
		}
		word(0xC80F + (op1.value shl 8))
	}

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

	private fun encodeMOV(node: InsNode) {
		if(node.size != 2) invalid()
		val op1 = node.op1
		val op2 = node.op2

		when(op1.type) {
			REG -> when(op2.type) {
				REG -> when {
					op1.reg.isR && op2.reg.isR  -> encode2RR(0x88, 0b1111, op2.reg, op1.reg)
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
					else        -> encode2RM(0x8A, 0b1111, op1.reg, op2, 0)
				}
				IMM -> encode1O(0xB0, 0b1111, op1.reg).imm(op2, op1.reg.width)
			}
			MEM -> when(op2.type) {
				REG -> when(op2.reg.type) {
					RegType.SEG -> encodeMOVMSEG(0x8C, op2.reg, op1)
					else        -> encode2RM(0x88, 0b1111, op2.reg, op1, 0)
				}
				IMM -> encode1M(0xC6, 0b1111, 0, op1, op1.immWidth().bytes).imm(op2, op1.immWidth())
				MEM -> invalid()
			}
			IMM -> invalid()
		}
	}

	private fun encodeCALL(node: InsNode) {
		if(node.size != 1) invalid()
		val op1 = node.op1
		when(op1.type) {
			REG -> encode1R(0xFF, 0b1000, 2, op1.reg)
			MEM -> encode1M(0xFF, 0b1000, 2, op1, 0)
			IMM -> byte(0xE8).rel(op1, DWORD)
		}
	}

	private fun encodeJMP(node: InsNode) {
		if(node.size != 1) invalid()
		val op1 = node.op1
		when(op1.type) {
			REG -> encode1R(0XFF, 0b1000, 4, op1.reg)
			MEM -> encode1M(0xFF, 0b1000, 4, op1, 0)
			IMM -> encode1Rel832(0xEB, 0xE9, op1)
		}
	}

	private fun encodeJCC(rel8Opcode: Int, rel32Opcode: Int, node: InsNode) {
		if(node.size != 1) invalid()
		encode1Rel832(rel8Opcode, rel32Opcode, node.op1)
	}

	private fun encodePUSH(node: InsNode) {
		if(node.size != 1) invalid()
		val op1 = node.op1
		when(op1.type) {
			REG -> when(op1.reg) {
				Reg.FS -> word(0xA00F)
				Reg.GS -> word(0xA80F)
				else   -> encode1O(0x50, 0b1010, op1.reg)
			}
			MEM -> encode1M(0xFF, 0b1010, 6, op1, 0)
			IMM -> {
				val imm = resolveImm(op1)

				fun i32() = byte(0x68).imm(imm, DWORD)
				fun i16() = word(0x6866).imm(imm, WORD)
				fun i8() = byte(0x6A).imm(imm, BYTE)

				when(op1.width) {
					null -> when {
						imm.hasReloc -> i32()
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

	private fun encodePOP(node: InsNode) {
		if(node.size != 1) invalid()
		val op1 = node.op1
		when(op1.type) {
			REG -> when(op1.reg) {
				Reg.FS -> word(0xA10F)
				Reg.GS -> word(0xA90F)
				else -> encode1O(0x58, 0b1010, op1.reg)
			}
			MEM -> encode1M(0x8F, 0b1010, 0, op1, 0)
			else -> invalid()
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



	/*
	Auto encoding
	 */



	private fun NasmEnc.writeSimdOpcode() { when(escape) {
		Escape.NONE -> byte(opcode)
		Escape.E0F  -> word(0x0F or (opcode shl 8))
		Escape.E38  -> i24(0x380F or (opcode shl 16))
		Escape.E3A  -> i24(0x3A0F or (opcode shl 16))
	} }

	private fun NasmEnc.writeSimdOpcode(addition: Int) { when(escape) {
		Escape.NONE -> byte(opcode + addition)
		Escape.E0F  -> word(0x0F or ((opcode + addition) shl 8))
		Escape.E38  -> i24(0x380F or ((opcode + addition) shl 16))
		Escape.E3A  -> i24(0x3A0F or ((opcode + addition) shl 16))
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



	private fun getEnc(mnemonic: Mnemonic, ops: AvxOps): NasmEnc? {
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

		val simdOps = AvxOps(
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



	private fun assembleAuto(node: InsNode) {
		if(node.high == 1)
			invalid("EVEX not currently supported")

		val r1 = node.op1.reg
		val r2 = node.op2.reg
		val r3 = node.op3.reg
		val r4 = node.op4.reg
		val memIndex: Int
		val mem: Mem?

		if(r1.type == RegType.ST) {
			assembleST(node, r1, r2)
			return
		}

		when {
			node.op1.isMem -> { memIndex = 1; mem = resolveMem(node.op1) }
			node.op2.isMem -> { memIndex = 2; mem = resolveMem(node.op2) }
			node.op3.isMem -> { memIndex = 3; mem = resolveMem(node.op3) }
			node.op4.isMem -> invalid()
			else           -> { memIndex = 0; mem = null }
		}

		var i8Node: OpNode? = null
		var i8 = 0

		when(node.size) {
			2 -> if(node.op2.isImm) { i8Node = node.op2; i8 = 1 }
			3 -> if(node.op3.isImm) { i8Node = node.op3; i8 = 1 }
			4 -> if(node.op4.isImm) { i8Node = node.op4; i8 = 1 }
		}

		val ops = AvxOps(
			i8,
			r1.type.ordinal,
			r2.type.ordinal,
			r3.type.ordinal,
			r4.type.ordinal,
			mem?.width?.let { it.ordinal + 1 } ?: 0,
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
			if(mem != null) {
				writeRex(enc.rw, r.rex, mem.rexX, mem.rexB, r1.rex8 or r2.rex8, r1.noRex or r2.noRex)
				enc.writeSimdOpcode()
				mem.write(r.value, i8)
			} else {
				writeRex(enc.rw, r.rex, 0, m.rex, r1.rex8 or r2.rex8, r1.noRex or r2.noRex)
				enc.writeSimdOpcode()
				writeModRM(0b11, r.value, m.value)
			}
		}

		when {
			r4 != Reg.NONE  -> byte(r4.index shl 4)
			i8Node != null  -> imm(i8Node, BYTE)
			enc.pseudo >= 0 -> byte(enc.pseudo)
		}
	}


}