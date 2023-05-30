package eyre

import eyre.Mnemonic.*
import eyre.Width.*

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



	private fun handleInstruction(node: InsNode) {
		node.prefix?.let { byte(it.value) }

		when {
			node.op1 == null -> assemble0(node)
			node.op2 == null -> assemble1(node, node.op1)
			node.op3 == null -> assemble2(node, node.op1, node.op2)
			node.op4 == null -> assemble3(node, node.op1, node.op2, node.op3)
			else             -> assemble4(node, node.op1, node.op2, node.op3, node.op4)
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

	private fun checkWidths(mask: OpMask, width: Width) {
		if(width !in mask) invalidEncoding()
	}

	private fun checkWidths(mask: OpMask, reg: Reg) {
		checkWidths(mask, reg.width)
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
	private fun rexW(opMask: OpMask, width: Width) =
		(width.bytes shr 3) and (opMask.value shr 2)

	/** Add one if width is not BYTE and if widths has BYTE set */
	private fun getOpcode(opcode: Int, opMask: OpMask, width: Width) =
		opcode + ((opMask.value and 1) and (1 shl width.ordinal).inv())

	private fun writeOpcode(opcode: Int) {
		writer.varLengthInt(opcode)
	}

	private fun writeOpcode(opcode: Int, opMask: OpMask, width: Width) {
		writer.varLengthInt(getOpcode(opcode, opMask, width))
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



	private fun encode1M(opcode: Int, mask: OpMask, node: MemNode) {
		val width = node.width ?: invalidEncoding()
		checkWidths(mask, node.width)
		checkO16(width)
		val disp = resolveMem(node.value)
		writeRex(rexW(mask, width), 0, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
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
		writeRex(rexW(opMask, width), 0, 0, op1.rex)
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
		writeRex(rexW(mask, width), op1.rex, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
		writeOpcode(getOpcode(opcode, mask, width))
		writeMem(op2, 0, disp, immLength)
	}



	/*
	Assembly
	 */



	private fun assemble0(node: InsNode) { when(node.mnemonic) {
		else -> invalidEncoding()
	} }



	private fun assemble1(node: InsNode, op1: OpNode) { when(node.mnemonic) {
		else -> invalidEncoding()
	} }



	private fun assemble2(node: InsNode, op1: OpNode, op2: OpNode) { when(node.mnemonic) {
		MOVUPS  -> { }
		//VMOVUPS -> encode2SMSM(vex { 0x10 + WIG + E00 + P0F }, op1, op2)
		//VMOVUPD -> encode2SMSM(vex { 0x10 + WIG + E66 + P0F }, op1, op2)
		else -> invalidEncoding()
	} }



	private fun assemble3(
		node: InsNode,
		op1: OpNode,
		op2: OpNode,
		op3: OpNode
	) { when(node.mnemonic) {
		else -> invalidEncoding()
	} }



	private fun assemble4(
		node: InsNode,
		op1: OpNode,
		op2: OpNode,
		op3: OpNode,
		op4: OpNode
	) { when(node.mnemonic) {
		else -> invalidEncoding()
	} }
	
	
	
	/*
	Manual encoding
	 */



	/*private fun encode2SS(info: VexInfo, op1: Reg, op2: Reg) {
		checkWidths(OpMask.SSE, op1)
		if(op1.width != op2.width) invalidEncoding()
		if(op1.high != 0 || op2.high != 0) invalidEncoding()

		val l = when(op1.width) {
			XWORD -> 0
			YWORD -> 1
			else  -> invalidEncoding()
		}

		if(op1.rex == 1 || op2.rex == 1 || info.requiresVex3) {
			byte(0xC4)
			writeVEX1(op1.rex xor 1, 1, op2.rex xor 1, info.prefix)
		} else {
			byte(0xC5)
		}

		writeVEX2(info.vexW, 0b1111, l, info.extension)
		byte(info.opcode)
		writeModRM(0b11, op1.value, op2.value)
	}



	private fun encode2SM(info: VexInfo, op1: Reg, op2: MemNode) {
		checkWidths(OpMask.SSE, op1)
		if(op2.width != null && op2.width != op1.width) invalidEncoding()
		if(op1.high != 0) invalidEncoding()

		val l = when(op1.width) {
			XWORD -> 0
			YWORD -> 1
			else  -> invalidEncoding()
		}

		val disp = resolveMem(op2.value)

		if(info.requiresVex3 || memRexB != 0 || memRexX != 0 || op1.rex == 1) {
			byte(0xC4)
			writeVEX1(op1.rex xor 1, memRexX xor 1, memRexB xor 1, info.prefix)
		} else {
			byte(0xC5)
		}

		writeVEX2(info.vexW, 0b1111, l, info.extension)
		byte(info.opcode)
		writeMem(op2, op1.value, disp, 0)
	}



	private fun encode2SSM(info: VexInfo, op1: RegNode, op2: OpNode) {
		when(op2) {
			is RegNode -> encode2SS(info, op1.value, op2.value)
			is MemNode -> encode2SM(info, op1.value, op2)
			else -> invalidEncoding()
		}
	}



	private fun encode2SMSM(info: VexInfo, op1: OpNode, op2: OpNode) {
		when(op1) {
			is RegNode -> when(op2) {
				is RegNode -> encode2SS(info, op1.value, op2.value)
				is MemNode -> encode2SM(info, op1.value, op2)
				else -> invalidEncoding()
			}
			is MemNode -> when(op2) {
				is RegNode -> encode2SM(info.incremented, op2.value, op1)
				else -> invalidEncoding()
			}
			else -> invalidEncoding()
		}
	}



	/**
	 * - Bits 0-7: opcode
	 * - Bits 8-8: VEX.W
	 * - Bits 9-10: prefix
	 * - Bits 11-12: extension
	 */
	@JvmInline
	value class VexInfo(val value: Int) {
		val opcode       get() = value and 0xFF
		val vexW         get() = (value shr 8) and 0b1
		val prefix       get() = (value shr 9) and 0b11
		val extension    get() = value shr 11
		val requiresVex3 get() = prefix > 1
		val incremented  get() = VexInfo(value + 1)

		companion object {
			const val E00 = 0 shl 11
			const val E66 = 1 shl 11
			const val EF3 = 2 shl 11
			const val EF2 = 3 shl 11
			const val P0F = 1 shl 9
			const val P38 = 2 shl 9
			const val P3A = 3 shl 9
			const val W0  = 0 shl 8
			const val W1  = 1 shl 8
			const val WIG = 1 shl 8
		}
	}

	private inline fun vex(block: VexInfo.Companion.() -> Int) = VexInfo(block(VexInfo))
	*/

}