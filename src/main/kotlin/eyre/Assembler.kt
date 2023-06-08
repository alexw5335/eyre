package eyre

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

		when(node.size) {
			0 -> invalidEncoding()
			1 -> invalidEncoding()
			2 -> invalidEncoding()
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


}