package eyre

import eyre.util.NativeWriter
import eyre.Mnemonic.*
import eyre.Width.*
import eyre.OpNodeType.*
import eyre.util.bin
import eyre.util.bin8

class Assembler(private val context: CompilerContext) {


	private val textWriter = context.textWriter

	private val dataWriter = context.dataWriter

	private val epilogueWriter = NativeWriter()

	private var writer = textWriter

	private var section = Section.TEXT

	private var rexRequired = false

	private var rexDisallowed = false



	fun assemble() {
		for(srcFile in context.srcFiles) {
			for(node in srcFile.nodes) {
				when(node) {
					is InsNode       -> handleInstruction(node)
					is LabelNode     -> handleLabel(node.symbol)
					is ProcNode      -> handleLabel(node.symbol)
					is ScopeEndNode  -> handleScopeEnd(node)
					//is VarResNode    -> handleVarRes(node)
					//is VarDbNode     -> handleVarDb(node)
					//is VarInitNode   -> handleVarInit(node)
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



	private fun invalidEncoding(): Nothing = error("Invalid encoding")



	/*
	Node assembly
	 */



	private fun handleInstruction(node: InsNode) {
		node.prefix?.let { byte(it.value) }

		when {
			node.op1 == null -> assemble0(node)
			node.op2 == null -> assemble1(node, node.op1)
			//node.op3 == null -> assemble2(node, node.op1, node.op2)
			//node.op4 == null -> assemble3(node, node.op1, node.op2, node.op3)
			else             -> invalidEncoding()
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
		if(node.symbol is ProcSymbol) {
			if(node.symbol.hasStackNodes) {
				//encodeRETURN()
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
			else -> invalidEncoding()
		}

		else -> error("Invalid imm node: $node")
	}



	private fun resolveImm(node: AstNode): Long {
		immRelocCount = 0
		return resolveImmRec(node, true)
	}



	private fun writeImm(node: OpNode, width: Width, hasImm64: Boolean = false) {
		writeImm(node, width, resolveImm(node), hasImm64)
	}



	private fun writeImm(
		node     : OpNode,
		width    : Width,
		value    : Long,
		hasImm64 : Boolean = false,
		isRel    : Boolean = false
	) {
		val actualWidth = if(width == QWORD && !hasImm64) DWORD else width
		if(node.width != null && node.width != actualWidth) invalidEncoding()

		if(isRel) {
			if(hasImmReloc)
				addRelReloc(actualWidth, node.node, 0)
			writer.writeWidth(actualWidth, value)
		} else if(immRelocCount == 1) {
			if(!hasImm64 || width != QWORD)
				error("Absolute relocations are only allowed with 64-bit operands")
			addAbsReloc(node.node)
			writer.advance(8)
		} else if(hasImmReloc) {
			addLinkReloc(actualWidth, node.node)
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
	private var aso = 0 // 0 = none, 1 = 32, 2 = 64
	private var memRelocCount = 0
	private val hasMemReloc get() = memRelocCount > 0



	private fun checkAso(width: Width) = when(width) {
		DWORD -> if(aso == 2) invalidEncoding() else aso = 1
		QWORD -> if(aso == 1) invalidEncoding() else aso = 2
		else  -> invalidEncoding()
	}



	private fun resolveMemRec(node: AstNode, regValid: Boolean): Long = when(node) {
		is IntNode    -> node.value
		is UnaryNode  -> node.calculate(::resolveMemRec, regValid)
		is BinaryNode -> {
			val regNode = node.left as? RegNode ?: node.right as? RegNode
			val intNode = node.left as? IntNode ?: node.right as? IntNode
			if(node.op == BinaryOp.MUL && regNode != null && intNode != null) {
				if(indexReg != null && !regValid) invalidEncoding()
				checkAso(regNode.value.width)
				indexReg = regNode.value
				indexScale = intNode.value.toInt()
				0
			} else
				node.calculate(::resolveMemRec, regValid)
		}
		is RegNode -> {
			if(!regValid) invalidEncoding()
			checkAso(node.value.width)
			if(baseReg != null) {
				if(indexReg != null) invalidEncoding()
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
			else -> invalidEncoding()
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
			if(indexScale != 1) invalidEncoding()
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
			invalidEncoding() // Empty memory operand
		}
	}




	/*
	Writing
	 */



	private val OpNode.asReg get() = if(type != REG) invalidEncoding() else reg

	private val OpNode.asMem get() = if(type != MEM) invalidEncoding() else this

	private val OpNode.asImm get() = if(type != IMM) invalidEncoding() else this

	private fun byte(value: Int) = writer.i8(value)

	private fun word(value: Int) = writer.i16(value)

	private fun dword(value: Int) = writer.i32(value)

	private fun checkWidth(mask: OpMask, width: Width) {
		if(width !in mask) invalidEncoding()
	}

	private fun writeO16(mask: OpMask, width: Width) {
		if(mask != OpMask.WORD && width == WORD) writer.i8(0x66)
	}

	private fun writeA32() {
		if(aso == 1) byte(0x67)
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

	private fun writePrefix(enc: Enc) {
		when(enc.prefix) {
			Enc.P66 -> byte(0x66)
			Enc.P67 -> byte(0x67)
			Enc.PF2 -> byte(0xF2)
			Enc.PF3 -> byte(0xF3)
		}
	}

	private fun writeEscape(enc: Enc) {
		when(enc.escape) {
			Enc.E0F -> byte(0x0F)
			Enc.E00 -> word(0x000F)
			Enc.E38 -> word(0x380F)
			Enc.E3A -> word(0x3A0F)
		}
	}

	private fun writeOpcode(enc: Enc, mask: OpMask, width: Width) {
		writeEscape(enc)
		writer.varLengthInt(getOpcode(enc.opcode, mask, width))
	}
	
	/** [w]: 64-bit override [r]: REG, [x]: INDEX, [b]: RM, BASE, or OPREG */
	private fun writeRex(w: Int, r: Int, x: Int, b: Int, forced: Int, banned: Int) {
		val value = (w shl 3) or (r shl 2) or (x shl 1) or b
		if(forced == 1 || value != 0)
			if(banned == 1)
				invalidEncoding()
			else
				byte(0b0100_0000 or value)
	}

	/** [w]: 64-bit override [r]: REG, [x]: INDEX, [b]: RM, BASE, or OPREG */
	private fun writeRex(w: Int, r: Int, x: Int, b: Int) {
		val value = (w shl 3) or (r shl 2) or (x shl 1) or b
		if(value != 0) byte(0b0100_0000 or value)
	}



	/*
	Encoding
	 */



	private fun encodeNone(enc: Enc, width: Width) {
		val mask = enc.mask
		checkWidth(mask, width)
		writeO16(mask, width)
		writePrefix(enc)
		if(rexw(mask, width) == 1) writer.i8(0x48)
		writeOpcode(enc, mask, width)
	}

	private fun Enc.encode1MEM(op1: OpNode) {
		if(op1.width != null) invalidEncoding()
		val disp = resolveMem(op1.node)
		writeA32()
		writePrefix(this)
		writeRex(rexw, indexReg?.rex ?: 0, 0, baseReg?.rex ?: 0)
		writeEscape(this)
		writer.varLengthInt(opcode)
		writeMem(op1.node, ext, disp, 0)
	}

	private fun Enc.encode1M(op1: OpNode, immLength: Int) {
		val mask = this.mask
		val width = op1.width ?: invalidEncoding()
		val disp = resolveMem(op1.node)
		checkWidth(mask, width)
		writeO16(mask, width)
		writeA32()
		writePrefix(this)
		writeRex(rexw or rexw(mask, width), 0, indexReg?.rex ?: 0, baseReg?.rex ?: 0)
		writeOpcode(this, mask, width)
		writeMem(op1.node, ext, disp, immLength)
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
		writeRex(rexw or rexw(mask, width), 0, 0, op1.rex)
		writeEscape(this)
		// Only single-byte opcodes use this encoding
		val opcode = this.opcode + ((mask.value and 1) and (1 shl width.ordinal).inv()) shl 3 + op1.value
		writer.varLengthInt(opcode)
	}

	private fun Enc.encode2RM(op1: Reg, op2: OpNode, immLength: Int) {
		val mask = this.mask
		val width = op1.width
		if(op2.width != null && op2.width != op1.width) invalidEncoding()
		val disp = resolveMem(op2.node)
		checkWidth(mask, width)
		writeO16(mask, width)
		writeA32()
		writePrefix(this)
		writeRex(rexw or rexw(mask, width), op1.rex, indexReg?.rex ?: 0, baseReg?.rex ?: 0, op1.rex8, op1.noRex)
		writeOpcode(this, mask, width)
		writeMem(op2.node, op1.value, disp, immLength)
	}

	private fun Enc.encode2RR(op1: Reg, op2: Reg) {
		val mask = this.mask
		val width = op1.width
		if(op1.width != op2.width) invalidEncoding()
		checkWidth(mask, width)
		writeO16(mask, width)
		writePrefix(this)
		writeRex(rexw or rexw(mask, width), op2.rex, 0, op1.rex, op1.rex8 or op2.rex8, op1.noRex or op2.noRex)
		writeOpcode(this, mask, width)
		writeModRM(0b11, op2.value, op2.value)
	}



	private fun Enc.encode1RM(op1: OpNode, immLength: Int) {
		when(op1.type) {
			REG -> encode1R(op1.reg)
			MEM -> encode1M(op1, immLength)
			IMM -> invalidEncoding()
		}
	}




	/*
	Assembly
	 */



	private fun assemble0(node: InsNode) { when(node.mnemonic) {
		INSB     -> writer.i8(0x6C)
		INSW     -> writer.i16(0x6D66)
		INSD     -> writer.i8(0x6D)
		OUTSB    -> writer.i8(0x6E)
		OUTSW    -> writer.i16(0x6F66)
		OUTSD    -> writer.i8(0x6F)
		NOP      -> writer.i8(0x90)
		PAUSE    -> writer.i16(0x90F3)
		CBW      -> writer.i16(0x9866)
		CWDE     -> writer.i8(0x98)
		CDQE     -> writer.i16(0x9848)
		CWD      -> writer.i16(0x9966)
		CDQ      -> writer.i8(0x99)
		CQO      -> writer.i16(0x9948)
		WAIT     -> writer.i8(0x9B)
		FWAIT    -> writer.i8(0x9B)
		PUSHFW   -> writer.i16(0x9C66)
		PUSHF    -> writer.i8(0x9C)
		PUSHFQ   -> writer.i8(0x9C)
		POPFW    -> writer.i16(0x9D66)
		POPF     -> writer.i8(0x9D)
		POPFQ    -> writer.i8(0x9D)
		SAHF     -> writer.i8(0x9E)
		LAHF     -> writer.i8(0x9F)
		MOVSB    -> writer.i8(0xA4)
		MOVSW    -> writer.i16(0xA566)
		MOVSQ    -> writer.i16(0xA548)
		CMPSB    -> writer.i8(0xA6)
		CMPSW    -> writer.i16(0xA766)
		CMPSQ    -> writer.i16(0xA748)
		STOSB    -> writer.i8(0xAA)
		STOSW    -> writer.i16(0xAB66)
		STOSD    -> writer.i8(0xAB)
		STOSQ    -> writer.i16(0xAB48)
		LODSB    -> writer.i8(0xAC)
		LODSW    -> writer.i16(0xAD66)
		LODSD    -> writer.i8(0xAD)
		LODSQ    -> writer.i16(0xAD48)
		SCASB    -> writer.i8(0xAE)
		SCASW    -> writer.i16(0xAF66)
		SCASD    -> writer.i8(0xAF)
		SCASQ    -> writer.i16(0xAF48)
		RET      -> writer.i8(0xC3)
		RETW     -> writer.i16(0xC366)
		RETF     -> writer.i8(0xCB)
		RETFQ    -> writer.i16(0xCB48)
		LEAVE    -> writer.i8(0xC9)
		LEAVEW   -> writer.i16(0xC966)
		INT3     -> writer.i8(0xCC)
		INT1     -> writer.i8(0xF1)
		ICEBP    -> writer.i8(0xF1)
		IRET     -> writer.i8(0xCF)
		IRETW    -> writer.i16(0xCF66)
		IRETD    -> writer.i8(0xCF)
		IRETQ    -> writer.i16(0xCF48)
		XLAT     -> writer.i8(0xD7)
		XLATB    -> writer.i8(0xD7)
		LOOPNZ   -> writer.i8(0xE0)
		LOOPNE   -> writer.i8(0xE0)
		LOOPZ    -> writer.i8(0xE1)
		LOOPE    -> writer.i8(0xE1)
		LOOP     -> writer.i8(0xE2)
		HLT      -> writer.i8(0xF4)
		CMC      -> writer.i8(0xF5)
		CLC      -> writer.i8(0xF8)
		STC      -> writer.i8(0xF9)
		CLI      -> writer.i8(0xFA)
		STI      -> writer.i8(0xFB)
		CLD      -> writer.i8(0xFC)
		STD      -> writer.i8(0xFD)
		F2XM1    -> writer.i16(0xF0D9)
		FABS     -> writer.i16(0xE1D9)
		FADD     -> writer.i16(0xC1DE)
		FADDP    -> writer.i16(0xC1DE)
		FCHS     -> writer.i16(0xE0D9)
		FCLEX    -> writer.i24(0xE2DB9B)
		FCMOVB   -> writer.i16(0xC1DA)
		FCMOVBE  -> writer.i16(0xD1DA)
		FCMOVE   -> writer.i16(0xC9DA)
		FCMOVNB  -> writer.i16(0xC1DB)
		FCMOVNBE -> writer.i16(0xD1DB)
		FCMOVNE  -> writer.i16(0xC9DB)
		FCMOVNU  -> writer.i16(0xD9DB)
		FCMOVU   -> writer.i16(0xD9DA)
		FCOM     -> writer.i16(0xD1D8)
		FCOMI    -> writer.i16(0xF1DB)
		FCOMIP   -> writer.i16(0xF1DF)
		FCOMP    -> writer.i16(0xD9D8)
		FCOMPP   -> writer.i16(0xD9DE)
		FCOS     -> writer.i16(0xFFD9)
		FDECSTP  -> writer.i16(0xF6D9)
		FDISI    -> writer.i24(0xE1DB9B)
		FDIV     -> writer.i16(0xF9DE)
		FDIVP    -> writer.i16(0xF9DE)
		FDIVR    -> writer.i16(0xF1DE)
		FDIVRP   -> writer.i16(0xF1DE)
		FENI     -> writer.i24(0xE0DB9B)
		FFREE    -> writer.i16(0xC1DD)
		FINCSTP  -> writer.i16(0xF7D9)
		FINIT    -> writer.i24(0xE3DB9B)
		FLD      -> writer.i16(0xC1D9)
		FLD1     -> writer.i16(0xE8D9)
		FLDL2E   -> writer.i16(0xEAD9)
		FLDL2T   -> writer.i16(0xE9D9)
		FLDLG2   -> writer.i16(0xECD9)
		FLDLN2   -> writer.i16(0xEDD9)
		FLDPI    -> writer.i16(0xEBD9)
		FLDZ     -> writer.i16(0xEED9)
		FMUL     -> writer.i16(0xC9DE)
		FMULP    -> writer.i16(0xC9DE)
		FNCLEX   -> writer.i16(0xE2DB)
		FNDISI   -> writer.i16(0xE1DB)
		FNENI    -> writer.i16(0xE0DB)
		FNINIT   -> writer.i16(0xE3DB)
		FNOP     -> writer.i16(0xD0D9)
		FPATAN   -> writer.i16(0xF3D9)
		FPREM    -> writer.i16(0xF8D9)
		FPREM1   -> writer.i16(0xF5D9)
		FPTAN    -> writer.i16(0xF2D9)
		FRNDINT  -> writer.i16(0xFCD9)
		FSCALE   -> writer.i16(0xFDD9)
		FSETPM   -> writer.i16(0xE4DB)
		FSIN     -> writer.i16(0xFED9)
		FSINCOS  -> writer.i16(0xFBD9)
		FSQRT    -> writer.i16(0xFAD9)
		FST      -> writer.i16(0xD1DD)
		FSTP     -> writer.i16(0xD9DD)
		FSUB     -> writer.i16(0xE9DE)
		FSUBP    -> writer.i16(0xE9DE)
		FSUBR    -> writer.i16(0xE1DE)
		FSUBRP   -> writer.i16(0xE1DE)
		FTST     -> writer.i16(0xE4D9)
		FUCOM    -> writer.i16(0xE1DD)
		FUCOMI   -> writer.i16(0xE9DB)
		FUCOMIP  -> writer.i16(0xE9DF)
		FUCOMP   -> writer.i16(0xE9DD)
		FUCOMPP  -> writer.i16(0xE9DA)
		FXAM     -> writer.i16(0xE5D9)
		FXCH     -> writer.i16(0xC9D9)
		FXTRACT  -> writer.i16(0xF4D9)
		FYL2X    -> writer.i16(0xF1D9)
		FYL2XP1  -> writer.i16(0xF9D9)
		ENCLV    -> writer.i24(0xC0010F)
		VMCALL   -> writer.i24(0xC1010F)
		VMLAUNCH -> writer.i24(0xC2010F)
		VMRESUME -> writer.i24(0xC3010F)
		VMXOFF   -> writer.i24(0xC4010F)
		CLAC     -> writer.i24(0xCA010F)
		STAC     -> writer.i24(0xCB010F)
		PCONFIG  -> writer.i24(0xC5010F)
		WRMSRNS  -> writer.i24(0xC6010F)
		MONITOR  -> writer.i24(0xC8010F)
		MWAIT    -> writer.i24(0xC9010F)
		ENCLS    -> writer.i24(0xCF010F)
		XGETBV   -> writer.i24(0xD0010F)
		XSETBV   -> writer.i24(0xD1010F)
		VMFUNC   -> writer.i24(0xD4010F)
		XEND     -> writer.i24(0xD5010F)
		XTEST    -> writer.i24(0xD6010F)
		ENCLU    -> writer.i24(0xD7010F)
		RDPKRU   -> writer.i24(0xEE010F)
		WRPKRU   -> writer.i24(0xEF010F)
		SWAPGS   -> writer.i24(0xF8010F)
		RDTSCP   -> writer.i24(0xF9010F)
		UIRET    -> writer.i32(0xEC010FF3)
		TESTUI   -> writer.i32(0xED010FF3)
		CLUI     -> writer.i32(0xEE010FF3)
		STUI     -> writer.i32(0xEF010FF3)
		SYSCALL  -> writer.i16(0x050F)
		CLTS     -> writer.i16(0x060F)
		SYSRET   -> writer.i16(0x070F)
		SYSRETQ  -> writer.i24(0x070F48)
		INVD     -> writer.i16(0x080F)
		WBINVD   -> writer.i16(0x090F)
		WBNOINVD -> writer.i24(0x090FF3)
		ENDBR32  -> writer.i32(0xFB1E0FF3)
		ENDBR64  -> writer.i32(0xFA1E0FF3)
		WRMSR    -> writer.i16(0x300F)
		RDTSC    -> writer.i16(0x310F)
		RDMSR    -> writer.i16(0x320F)
		RDPMC    -> writer.i16(0x330F)
		SYSENTER -> writer.i16(0x340F)
		SYSEXIT  -> writer.i16(0x350F)
		SYSEXITQ -> writer.i24(0x350F48)
		GETSEC   -> writer.i16(0x370F)
		EMMS     -> writer.i16(0x770F)
		CPUID    -> writer.i16(0xA20F)
		RSM      -> writer.i16(0xAA0F)
		LFENCE   -> writer.i24(0xE8AE0F)
		MFENCE   -> writer.i24(0xF0AE0F)
		SFENCE   -> writer.i24(0xF8AE0F)
		SERIALIZE -> writer.i24(0xE8010F)
		XSUSLDTRK -> writer.i32(0xE8010FF2)
		XRESLDTRK -> writer.i32(0xE9010FF2)
		SETSSBSY -> writer.i32(0xE8010FF3)
		SAVEPREVSSP -> writer.i32(0xEA010FF3)
		else -> invalidEncoding()
	}}



	private fun assemble1(node: InsNode, op1: OpNode) { when(node.mnemonic) {
		PUSH  -> encodePUSH(op1)
		POP   -> encodePOP(op1)
		PUSHW -> encodePUSHW(op1)
		POPW  -> encodePOPW(op1)
		NOT   -> Enc { 0xF6 + EXT2 + R1111 }.encode1RM(op1, 0)
		NEG   -> Enc { 0xF6 + EXT3 + R1111 }.encode1RM(op1, 0)
		MUL   -> Enc { 0xF6 + EXT4 + R1111 }.encode1RM(op1, 0)
		IMUL  -> Enc { 0xF6 + EXT5 + R1111 }.encode1RM(op1, 0)
		DIV   -> Enc { 0xF6 + EXT6 + R1111 }.encode1RM(op1, 0)
		IDIV  -> Enc { 0xF6 + EXT7 + R1111 }.encode1RM(op1, 0)
		INC   -> Enc { 0xFE + EXT0 + R1111 }.encode1RM(op1, 0)
		DEC   -> Enc { 0xFE + EXT1 + R1111 }.encode1RM(op1, 0)
		NOP   -> Enc { E0F + 0x1F + R0110 }.encode1RM(op1, 0)


		else -> invalidEncoding()
	}}




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
			MEM -> Enc { 0xFF + R1110 + EXT6 }.encode1M(op1, 0)
			IMM -> {
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
	private fun encodePOP(op1: OpNode) {
		when(op1.type) {
			REG -> when(op1.reg) {
				Reg.FS -> word(0xA10F)
				Reg.GS -> word(0xA90F)
				else -> Enc { 0x58 + R1010 }.encode1O(op1.reg)
			}
			MEM -> Enc { 0x8F + R1110 + EXT0 }.encode1M(op1)
			else -> invalidEncoding()
		}
	}

	/**
	 *     A00F66  PUSHW  FS
	 *     A80F66  PUSHW  GS
	 */
	private fun encodePUSHW(op1: OpNode) {
		when(op1.asReg) {
			Reg.FS -> writer.i24(0xA80F66)
			Reg.GS -> writer.i32(0xA80F66)
			else   -> invalidEncoding()
		}
	}

	/**
	 *     A10F66  POPW   FS
	 *     A90F66  POPW   GS
	 */
	private fun encodePOPW(op1: OpNode) {
		when(op1.asReg) {
			Reg.FS -> writer.i24(0xA10F66)
			Reg.GS -> writer.i32(0xA10F66)
			else   -> invalidEncoding()
		}
	}


}