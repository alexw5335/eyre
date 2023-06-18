package eyre

import eyre.util.NativeWriter
import eyre.Mnemonic.*
import eyre.Width.*
import eyre.OpNodeType.*

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
	Writing
	 */



	private val OpNode.asReg get() = if(type != REG) invalidEncoding() else reg

	private fun byte(value: Int) = writer.i8(value)

	private fun word(value: Int) = writer.i16(value)

	private fun dword(value: Int) = writer.i32(value)

	private fun checkWidths(mask: OpMask, width: Width) {
		if(width !in mask) invalidEncoding()
	}

	private fun writeO16(mask: OpMask, width: Width) {
		if(mask != OpMask.WORD && width == WORD) writer.i8(0x66)
	}

	/** [w]: 64-bit override [r]: REG, [x]: INDEX, [b]: RM, BASE, or OPREG */
	private fun writeRex(w: Int, r: Int, x: Int, b: Int) {
		val value = 0b0100_0000 or (w shl 3) or (r shl 2) or (x shl 1) or b
		if(rexRequired || value != 0b0100_0000)
			if(rexDisallowed)
				invalidEncoding()
			else
				writer.i8(value)
	}

	private fun writeModRM(mod: Int, reg: Int, rm: Int) {
		writer.i8((mod shl 6) or (reg shl 3) or rm)
	}

	private fun writeSib(scale: Int, index: Int, base: Int) {
		writer.i8((scale shl 6) or (index shl 3) or base)
	}

	/** Return 1 if width is QWORD and widths has DWORD set, otherwise 0 */
	private fun rexW(mask: OpMask, width: Width) =
		(width.bytes shr 3) and (mask.value shr 2)

	/** Add one if width is not BYTE and if widths has BYTE set */
	private fun getOpcode(opcode: Int, widths: OpMask, width: Width) =
		opcode + ((widths.value and 1) and (1 shl width.ordinal).inv())

	private fun preRex(enc: Enc, mask: OpMask, width: Width) {
		checkWidths(mask, width)
		writeO16(mask, width)
		writePrefix(enc)
	}

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

	private fun writeOpcode(enc: Enc) {
		writeEscape(enc)
		writer.varLengthInt(enc.opcode)
	}

	private fun writeOpcode(enc: Enc, mask: OpMask, width: Width) {
		writeEscape(enc)
		writer.varLengthInt(getOpcode(enc.opcode, mask, width))
	}

	private fun writeModRM(
		enc: Enc,
		mask  : OpMask,
		width : Width,
		rex   : Rex,
		modrm : ModRM
	) {
		checkWidths(mask, width)
		writeO16(mask, width)
		writePrefix(enc)
		if(rex.forced || rex.rex != 0)
			if(rex.banned)
				invalidEncoding()
			else
				byte(0b0100_0000 or rex.rex)
		writeOpcode(enc, mask, width)
		byte(modrm.modrm)
	}



	/*
	Encoding
	 */



	private fun encodeNone(enc: Enc, mask: OpMask, width: Width) {
		preRex(enc, mask, width)
		writeRex(rexW(mask, width), 0, 0, 0)
		writeOpcode(enc, mask, width)
	}

	private fun encode1R(enc: Enc, mask: OpMask, op1: Reg) =
		writeModRM(
			enc,
			mask,
			op1.width,
			Rex(rexW(mask, op1.width), 0, 0, op1.rex, op1.rex8, op1.noRex),
			ModRM(0b11, enc.ext, op1.value)
		)

	private fun encode2RR(enc: Enc, mask: OpMask, op1: Reg, op2: Reg) =
		writeModRM(
			enc,
			mask,
			op1.width,
			Rex(rexW(mask, op1.width), op2.rex, 0, op1.rex, op1.rex8 or op2.rex8, op1.noRex or op2.noRex),
			ModRM(0b11, op2.value, op1.value)
		)



	/*
	Assembly
	 */



	private fun assemble0(node: InsNode) { when(node.mnemonic) {
		INSB   -> writer.i8(0x6C)
		INSW   -> writer.i16(0x6D66)
		INSD   -> writer.i8(0x6D)
		OUTSB  -> writer.i8(0x6E)
		OUTSW  -> writer.i16(0x6F66)
		OUTSD  -> writer.i8(0x6F)
		NOP    -> writer.i8(0x90)
		PAUSE  -> writer.i16(0x90F3)
		CBW    -> writer.i16(0x9866)
		CWDE   -> writer.i8(0x98)
		CDQE   -> writer.i16(0x9848)
		CWD    -> writer.i16(0x9966)
		CDQ    -> writer.i8(0x99)
		CQO    -> writer.i16(0x9948)
		WAIT   -> writer.i8(0x9B)
		FWAIT  -> writer.i8(0x9B)
		PUSHFW -> writer.i16(0x9C66)
		PUSHF  -> writer.i8(0x9C)
		PUSHFQ -> writer.i8(0x9C)
		POPFW  -> writer.i16(0x9D66)
		POPF   -> writer.i8(0x9D)
		POPFQ  -> writer.i8(0x9D)
		SAHF   -> writer.i8(0x9E)
		LAHF   -> writer.i8(0x9F)
		MOVSB  -> writer.i8(0xA4)
		MOVSW  -> writer.i16(0xA566)
		MOVSQ  -> writer.i16(0xA548)
		CMPSB  -> writer.i8(0xA6)
		CMPSW  -> writer.i16(0xA766)
		CMPSQ  -> writer.i16(0xA748)
		STOSB  -> writer.i8(0xAA)
		STOSW  -> writer.i16(0xAB66)
		STOSD  -> writer.i8(0xAB)
		STOSQ  -> writer.i16(0xAB48)
		LODSB  -> writer.i8(0xAC)
		LODSW  -> writer.i16(0xAD66)
		LODSD  -> writer.i8(0xAD)
		LODSQ  -> writer.i16(0xAD48)
		SCASB  -> writer.i8(0xAE)
		SCASW  -> writer.i16(0xAF66)
		SCASD  -> writer.i8(0xAF)
		SCASQ  -> writer.i16(0xAF48)
		RET    -> writer.i8(0xC3)
		RETW   -> writer.i16(0xC366)
		RETF   -> writer.i8(0xCB)
		RETFQ  -> writer.i16(0xCB48)
		LEAVE  -> writer.i8(0xC9)
		LEAVEW -> writer.i16(0xC966)
		INT3   -> writer.i8(0xCC)
		INT1   -> writer.i8(0xF1)
		ICEBP  -> writer.i8(0xF1)
		IRET   -> writer.i8(0xCF)
		IRETW  -> writer.i16(0xCF66)
		IRETD  -> writer.i8(0xCF)
		IRETQ  -> writer.i16(0xCF48)
		XLAT   -> writer.i8(0xD7)
		XLATB  -> writer.i8(0xD7)
		HLT    -> writer.i8(0xF4)
		CMC    -> writer.i8(0xF5)
		CLC    -> writer.i8(0xF8)
		STC    -> writer.i8(0xF9)
		CLI    -> writer.i8(0xFA)
		STI    -> writer.i8(0xFB)
		CLD    -> writer.i8(0xFC)
		STD    -> writer.i8(0xFD)
		F2XM1  -> writer.i16(0xD9F0)
		FABS   -> writer.i16(0xD9E1)
		FADD   -> writer.i16(0xDEC1)
		FADDP  -> writer.i16(0xDEC1)
		FCHS   -> writer.i16(0xD9E0)
		FCLEX  -> writer.i24(0xDBE29B)
		FCMOVB -> writer.i16(0xDAC1)
		FCMOVBE  -> writer.i16(0xDAD1)
		FCMOVE   -> writer.i16(0xDAC9)
		FCMOVNB  -> writer.i16(0xDBC1)
		FCMOVNBE -> writer.i16(0xDBD1)
		FCMOVNE  -> writer.i16(0xDBC9)
		FCMOVNU  -> writer.i16(0xDBD9)
		FCMOVU   -> writer.i16(0xDAD9)
		FCOM     -> writer.i16(0xD8D1)
		FCOMI    -> writer.i16(0xDBF1)
		FCOMIP   -> writer.i16(0xDFF1)
		FCOMP    -> writer.i16(0xD8D9)
		FCOMPP   -> writer.i16(0xDED9)
		FCOS     -> writer.i16(0xD9FF)
		FDECSTP  -> writer.i16(0xD9F6)
		FDISI    -> writer.i24(0xDBE19B)
		FDIV     -> writer.i16(0xDEF9)
		FDIVP    -> writer.i16(0xDEF9)
		FDIVR    -> writer.i16(0xDEF1)
		FDIVRP   -> writer.i16(0xDEF1)
		FENI     -> writer.i24(0xDBE09B)
		FFREE    -> writer.i16(0xDDC1)
		FINCSTP  -> writer.i16(0xD9F7)
		FINIT    -> writer.i24(0xDBE39B)
		FLD      -> writer.i16(0xD9C1)
		FLD1     -> writer.i16(0xD9E8)
		FLDL2E   -> writer.i16(0xD9EA)
		FLDL2T   -> writer.i16(0xD9E9)
		FLDLG2   -> writer.i16(0xD9EC)
		FLDLN2   -> writer.i16(0xD9ED)
		FLDPI    -> writer.i16(0xD9EB)
		FLDZ     -> writer.i16(0xD9EE)
		FMUL     -> writer.i16(0xDEC9)
		FMULP    -> writer.i16(0xDEC9)
		FNCLEX   -> writer.i16(0xDBE2)
		FNDISI   -> writer.i16(0xDBE1)
		FNENI    -> writer.i16(0xDBE0)
		FNINIT   -> writer.i16(0xDBE3)
		FNOP     -> writer.i16(0xD9D0)
		FPATAN   -> writer.i16(0xD9F3)
		FPREM    -> writer.i16(0xD9F8)
		FPREM1   -> writer.i16(0xD9F5)
		FPTAN    -> writer.i16(0xD9F2)
		FRNDINT  -> writer.i16(0xD9FC)
		FSCALE   -> writer.i16(0xD9FD)
		FSETPM   -> writer.i16(0xDBE4)
		FSIN     -> writer.i16(0xD9FE)
		FSINCOS  -> writer.i16(0xD9FB)
		FSQRT    -> writer.i16(0xD9FA)
		FST      -> writer.i16(0xDDD1)
		FSTP     -> writer.i16(0xDDD9)
		FSUB     -> writer.i16(0xDEE9)
		FSUBP    -> writer.i16(0xDEE9)
		FSUBR    -> writer.i16(0xDEE1)
		FSUBRP   -> writer.i16(0xDEE1)
		FTST     -> writer.i16(0xD9E4)
		FUCOM    -> writer.i16(0xDDE1)
		FUCOMI   -> writer.i16(0xDBE9)
		FUCOMIP  -> writer.i16(0xDFE9)
		FUCOMP   -> writer.i16(0xDDE9)
		FUCOMPP  -> writer.i16(0xDAE9)
		FXAM     -> writer.i16(0xD9E5)
		FXCH     -> writer.i16(0xD9C9)
		FXTRACT  -> writer.i16(0xD9F4)
		FYL2X    -> writer.i16(0xD9F1)
		FYL2XP1  -> writer.i16(0xD9F9)
		ENCLV    -> writer.i24(0x01C00F)
		VMCALL   -> writer.i24(0x01C10F)
		VMLAUNCH -> writer.i24(0x01C20F)
		VMRESUME -> writer.i24(0x01C30F)
		VMXOFF   -> writer.i24(0x01C40F)
		CLAC     -> writer.i24(0x01CA0F)
		STAC     -> writer.i24(0x01CB0F)
		PCONFIG  -> writer.i24(0x01C50F)
		WRMSRNS  -> writer.i24(0x01C60F)
		MONITOR  -> writer.i24(0x01C80F)
		MWAIT    -> writer.i24(0x01C90F)
		ENCLS    -> writer.i24(0x01CF0F)
		XGETBV   -> writer.i24(0x01D00F)
		XSETBV   -> writer.i24(0x01D10F)
		VMFUNC   -> writer.i24(0x01D40F)
		XEND     -> writer.i24(0x01D50F)
		XTEST    -> writer.i24(0x01D60F)
		ENCLU    -> writer.i24(0x01D70F)
		SERIALIZE   -> writer.i24(0x01E80F)
		RDPKRU      -> writer.i24(0x01EE0F)
		WRPKRU      -> writer.i24(0x01EF0F)
		SWAPGS      -> writer.i24(0x01F80F)
		RDTSCP      -> writer.i24(0x01F90F)
		XSUSLDTRK   -> writer.i32(0x01E80FF2)
		XRESLDTRK   -> writer.i32(0x01E90FF2)
		SETSSBSY    -> writer.i32(0x01E80FF3)
		SAVEPREVSSP -> writer.i32(0x01EA0FF3)
		UIRET    -> writer.i32(0x01EC0FF3)
		TESTUI   -> writer.i32(0x01ED0FF3)
		CLUI     -> writer.i32(0x01EE0FF3)
		STUI     -> writer.i32(0x01EF0FF3)
		SYSCALL  -> writer.i16(0x050F)
		CLTS     -> writer.i16(0x060F)
		SYSRET   -> writer.i16(0x070F)
		SYSRETQ  -> writer.i24(0x070F48)
		INVD     -> writer.i16(0x080F)
		WBINVD   -> writer.i16(0x090F)
		WBNOINVD -> writer.i24(0x090FF3)
		UD1      -> writer.i16(0xB90F)
		UD2      -> writer.i16(0x0B0F)
		ENDBR32  -> writer.i32(0x1EFB0FF3)
		ENDBR64  -> writer.i32(0x1EFA0FF3)
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
		LFENCE   -> writer.i24(0xAEE80F)
		MFENCE   -> writer.i24(0xAEF00F)
		SFENCE   -> writer.i24(0xAEF80F)
		else     -> invalidEncoding()
	}}



	private fun assemble1(node: InsNode, op1: OpNode) { when(node.mnemonic) {
		NOT  -> encode1R(Enc { 0xF6 + EXT2 }, OpMask.R1111, op1.asReg)
		else -> invalidEncoding()
	}}


}