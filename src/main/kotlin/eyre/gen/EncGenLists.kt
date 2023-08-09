package eyre.gen

import eyre.Mnemonic
import eyre.Width
import eyre.NasmOp

object EncGenLists {

	val arches     = NasmArch.entries.associateBy { it.name.trimStart('_') }
	val extensions = NasmExt.entries.associateBy { it.name.trimStart('_') }
	val vsibs      = NasmVsib.entries.associateBy { it.name.lowercase() }
	val immTypes   = NasmImm.entries.associateBy { it.name.lowercase().replace('_', ',') }
	val tupleTypes = NasmTuple.entries.associateBy { it.name.lowercase() }
	val opEncs     = NasmOpEnc.entries.associateBy { it.string }
	val ops        = NasmOp.entries.associateBy { it.nasmString }
	val mnemonics  = Mnemonic.entries.associateBy { it.name }

	val mrEncs = setOf(NasmOpEnc.MR, NasmOpEnc.MRN, NasmOpEnc.MRX, NasmOpEnc.MRI)

	val ccList = arrayOf<Pair<String, Int>>(
		"O" to 0,
		"NO" to 1,
		"B" to 2, "NAE" to 2, "C" to 2,
		"NB" to 3, "AE" to 3, "NC" to 3,
		"Z" to 4, "E" to 4,
		"NZ" to 5, "NE" to 5,
		"BE" to 6, "NA" to 6,
		"NBE" to 7, "A" to 7,
		"S" to 8,
		"NS" to 9,
		"P" to 10, "PE" to 10,
		"NP" to 11, "PO" to 11,
		"L" to 12, "NGE" to 12,
		"NL" to 13, "GE" to 13,
		"LE" to 14, "NG" to 14,
		"NLE" to 15, "G" to 15
	)

	val opWidths = mapOf(
		"SB" to Width.BYTE,
		"SW" to Width.WORD,
		"SD" to Width.DWORD,
		"SQ" to Width.QWORD,
		"SO" to Width.XWORD,
		"SY" to Width.YWORD,
		"SZ" to Width.ZWORD
	)

	val pseudoMnemonics = setOf(
		"DLLCALL",
		"RETURN"
	)

	val additionalMnemonics = setOf(
		// Custom
		"POPW",
		"PUSHW",
		"LEAVEW",
		"ENTER",
		"WAIT",
		"JMPF",
		"CALLF",
		"ENTERW",
		"SYSEXITQ",
		"SYSRETQ",
		// Found in Intel manual but not in NASM
		"AOR",
	)

	val invalidMnemonics = setOf(
		// Obsolete
		"JMPE",
		// Unnecessary size specifiers
		"RETN",
		"RETQ",
		"RETNW",
		"RETNQ",
		"RETFW",
		"RETFD",
		// Not found in Intel manual? Pseudo-mnemonics
		"PREFETCHIT1",
		"PREFETCHIT0",
		"UD2B",
		"UD2A",
		"INT01",
		"INT03",
		"SKINIT",
	)

	val essentialMnemonics = setOf(
		"SYSCALL",
		"SYSRET",
		"LZCNT",
		"PREFETCHW",
		"SAL"
	)

	val invalidExtras = setOf(
		"NOLONG",
		"NEVER",
		"UNDOC",
		"OBSOLETE",
		"AMD",
		"CYRIX",
		"LATEVEX",
		"OPT",
		"3DNOW",
		"TBM"
	)

	val invalidOperands = setOf(
		"sbyte",
		"fpureg|to",
		"xmm0",
		"imm64|near",
		"mem|far",
		"mem16|far",
		"mem32|far",
		"mem64|far"
	)

	val ignoredExtras = setOf(
		"DEFAULT",
		"ANY",
		"VEX",
		"EVEX",
		"NOP",
		"HLE",
		"NOHLE",
		"PRIV",
		"SMM",
		"PROT",
		"LOCK",
		"LONG",
		"BND",
		"MIB",
		"SIB",
		"SIZE",
		"ANYSIZE",
		"ND",
		"SX",
		"AMD"
	)

	val ignoredParts = setOf(
		"hle",
		"nof3",
		"hlenl",
		"hlexr",
		"adf",
		"norexb",
		"norexx",
		"norexr",
		"norexw",
		"nohi",
		"nof3",
		"norep",
		"repe",
		"np",
		"iwdq",
		"a64",
		"o32",
		"o64nw"
	)
	
	val zeroOpOpcodes = mapOf<Mnemonic, Int>(
		Mnemonic.INSB        to 0x6C,
		Mnemonic.INSW        to 0x6D66,
		Mnemonic.INSD        to 0x6D,
		Mnemonic.OUTSB       to 0x6E,
		Mnemonic.OUTSW       to 0x6F66,
		Mnemonic.OUTSD       to 0x6F,
		Mnemonic.NOP         to 0x90,
		Mnemonic.PAUSE       to 0x90F3,
		Mnemonic.CBW         to 0x9866,
		Mnemonic.CWDE        to 0x98,
		Mnemonic.CDQE        to 0x9848,
		Mnemonic.CWD         to 0x9966,
		Mnemonic.CDQ         to 0x99,
		Mnemonic.CQO         to 0x9948,
		Mnemonic.WAIT        to 0x9B,
		Mnemonic.FWAIT       to 0x9B,
		Mnemonic.PUSHFW      to 0x9C66,
		Mnemonic.PUSHF       to 0x9C,
		Mnemonic.PUSHFQ      to 0x9C,
		Mnemonic.POPFW       to 0x9D66,
		Mnemonic.POPF        to 0x9D,
		Mnemonic.POPFQ       to 0x9D,
		Mnemonic.SAHF        to 0x9E,
		Mnemonic.LAHF        to 0x9F,
		Mnemonic.MOVSB       to 0xA4,
		Mnemonic.MOVSW       to 0xA566,
		Mnemonic.MOVSD       to 0xA5,
		Mnemonic.MOVSQ       to 0xA548,
		Mnemonic.CMPSB       to 0xA6,
		Mnemonic.CMPSW       to 0xA766,
		Mnemonic.CMPSD       to 0xA7,
		Mnemonic.CMPSQ       to 0xA748,
		Mnemonic.STOSB       to 0xAA,
		Mnemonic.STOSW       to 0xAB66,
		Mnemonic.STOSD       to 0xAB,
		Mnemonic.STOSQ       to 0xAB48,
		Mnemonic.LODSB       to 0xAC,
		Mnemonic.LODSW       to 0xAD66,
		Mnemonic.LODSD       to 0xAD,
		Mnemonic.LODSQ       to 0xAD48,
		Mnemonic.SCASB       to 0xAE,
		Mnemonic.SCASW       to 0xAF66,
		Mnemonic.SCASD       to 0xAF,
		Mnemonic.SCASQ       to 0xAF48,
		Mnemonic.RET         to 0xC3,
		Mnemonic.RETW        to 0xC366,
		Mnemonic.RETF        to 0xCB,
		Mnemonic.RETFQ       to 0xCB48,
		Mnemonic.LEAVE       to 0xC9,
		Mnemonic.LEAVEW      to 0xC966,
		Mnemonic.INT3        to 0xCC,
		Mnemonic.INT1        to 0xF1,
		Mnemonic.ICEBP       to 0xF1,
		Mnemonic.IRET        to 0xCF,
		Mnemonic.IRETW       to 0xCF66,
		Mnemonic.IRETD       to 0xCF,
		Mnemonic.IRETQ       to 0xCF48,
		Mnemonic.XLAT        to 0xD7,
		Mnemonic.XLATB       to 0xD7,
		Mnemonic.LOOPNZ      to 0xE0,
		Mnemonic.LOOPNE      to 0xE0,
		Mnemonic.LOOPZ       to 0xE1,
		Mnemonic.LOOPE       to 0xE1,
		Mnemonic.LOOP        to 0xE2,
		Mnemonic.HLT         to 0xF4,
		Mnemonic.CMC         to 0xF5,
		Mnemonic.CLC         to 0xF8,
		Mnemonic.STC         to 0xF9,
		Mnemonic.CLI         to 0xFA,
		Mnemonic.STI         to 0xFB,
		Mnemonic.CLD         to 0xFC,
		Mnemonic.STD         to 0xFD,
		Mnemonic.F2XM1       to 0xF0D9,
		Mnemonic.FABS        to 0xE1D9,
		Mnemonic.FADD        to 0xC1DE,
		Mnemonic.FADDP       to 0xC1DE,
		Mnemonic.FCHS        to 0xE0D9,
		Mnemonic.FCLEX       to 0xE2DB9B,
		Mnemonic.FCMOVB      to 0xC1DA,
		Mnemonic.FCMOVBE     to 0xD1DA,
		Mnemonic.FCMOVE      to 0xC9DA,
		Mnemonic.FCMOVNB     to 0xC1DB,
		Mnemonic.FCMOVNBE    to 0xD1DB,
		Mnemonic.FCMOVNE     to 0xC9DB,
		Mnemonic.FCMOVNU     to 0xD9DB,
		Mnemonic.FCMOVU      to 0xD9DA,
		Mnemonic.FCOM        to 0xD1D8,
		Mnemonic.FCOMI       to 0xF1DB,
		Mnemonic.FCOMIP      to 0xF1DF,
		Mnemonic.FCOMP       to 0xD9D8,
		Mnemonic.FCOMPP      to 0xD9DE,
		Mnemonic.FCOS        to 0xFFD9,
		Mnemonic.FDECSTP     to 0xF6D9,
		Mnemonic.FDISI       to 0xE1DB9B,
		Mnemonic.FDIV        to 0xF9DE,
		Mnemonic.FDIVP       to 0xF9DE,
		Mnemonic.FDIVR       to 0xF1DE,
		Mnemonic.FDIVRP      to 0xF1DE,
		Mnemonic.FENI        to 0xE0DB9B,
		Mnemonic.FFREE       to 0xC1DD,
		Mnemonic.FINCSTP     to 0xF7D9,
		Mnemonic.FINIT       to 0xE3DB9B,
		Mnemonic.FLD         to 0xC1D9,
		Mnemonic.FLD1        to 0xE8D9,
		Mnemonic.FLDL2E      to 0xEAD9,
		Mnemonic.FLDL2T      to 0xE9D9,
		Mnemonic.FLDLG2      to 0xECD9,
		Mnemonic.FLDLN2      to 0xEDD9,
		Mnemonic.FLDPI       to 0xEBD9,
		Mnemonic.FLDZ        to 0xEED9,
		Mnemonic.FMUL        to 0xC9DE,
		Mnemonic.FMULP       to 0xC9DE,
		Mnemonic.FNCLEX      to 0xE2DB,
		Mnemonic.FNDISI      to 0xE1DB,
		Mnemonic.FNENI       to 0xE0DB,
		Mnemonic.FNINIT      to 0xE3DB,
		Mnemonic.FNOP        to 0xD0D9,
		Mnemonic.FPATAN      to 0xF3D9,
		Mnemonic.FPREM       to 0xF8D9,
		Mnemonic.FPREM1      to 0xF5D9,
		Mnemonic.FPTAN       to 0xF2D9,
		Mnemonic.FRNDINT     to 0xFCD9,
		Mnemonic.FSCALE      to 0xFDD9,
		Mnemonic.FSETPM      to 0xE4DB,
		Mnemonic.FSIN        to 0xFED9,
		Mnemonic.FSINCOS     to 0xFBD9,
		Mnemonic.FSQRT       to 0xFAD9,
		Mnemonic.FST         to 0xD1DD,
		Mnemonic.FSTP        to 0xD9DD,
		Mnemonic.FSUB        to 0xE9DE,
		Mnemonic.FSUBP       to 0xE9DE,
		Mnemonic.FSUBR       to 0xE1DE,
		Mnemonic.FSUBRP      to 0xE1DE,
		Mnemonic.FTST        to 0xE4D9,
		Mnemonic.FUCOM       to 0xE1DD,
		Mnemonic.FUCOMI      to 0xE9DB,
		Mnemonic.FUCOMIP     to 0xE9DF,
		Mnemonic.FUCOMP      to 0xE9DD,
		Mnemonic.FUCOMPP     to 0xE9DA,
		Mnemonic.FXAM        to 0xE5D9,
		Mnemonic.FXCH        to 0xC9D9,
		Mnemonic.FXTRACT     to 0xF4D9,
		Mnemonic.FYL2X       to 0xF1D9,
		Mnemonic.FYL2XP1     to 0xF9D9,
		Mnemonic.ENCLV       to 0xC0010F,
		Mnemonic.VMCALL      to 0xC1010F,
		Mnemonic.VMLAUNCH    to 0xC2010F,
		Mnemonic.VMRESUME    to 0xC3010F,
		Mnemonic.VMXOFF      to 0xC4010F,
		Mnemonic.CLAC        to 0xCA010F,
		Mnemonic.STAC        to 0xCB010F,
		Mnemonic.PCONFIG     to 0xC5010F,
		Mnemonic.WRMSRNS     to 0xC6010F,
		Mnemonic.MONITOR     to 0xC8010F,
		Mnemonic.MWAIT       to 0xC9010F,
		Mnemonic.ENCLS       to 0xCF010F,
		Mnemonic.XGETBV      to 0xD0010F,
		Mnemonic.XSETBV      to 0xD1010F,
		Mnemonic.VMFUNC      to 0xD4010F,
		Mnemonic.XEND        to 0xD5010F,
		Mnemonic.XTEST       to 0xD6010F,
		Mnemonic.ENCLU       to 0xD7010F,
		Mnemonic.RDPKRU      to 0xEE010F,
		Mnemonic.WRPKRU      to 0xEF010F,
		Mnemonic.SWAPGS      to 0xF8010F,
		Mnemonic.RDTSCP      to 0xF9010F,
		Mnemonic.UIRET       to 0xEC010FF3U.toInt(),
		Mnemonic.TESTUI      to 0xED010FF3U.toInt(),
		Mnemonic.CLUI        to 0xEE010FF3U.toInt(),
		Mnemonic.STUI        to 0xEF010FF3U.toInt(),
		Mnemonic.SYSCALL     to 0x050F,
		Mnemonic.CLTS        to 0x060F,
		Mnemonic.SYSRET      to 0x070F,
		Mnemonic.SYSRETQ     to 0x070F48,
		Mnemonic.INVD        to 0x080F,
		Mnemonic.WBINVD      to 0x090F,
		Mnemonic.WBNOINVD    to 0x090FF3,
		Mnemonic.ENDBR32     to 0xFB1E0FF3U.toInt(),
		Mnemonic.ENDBR64     to 0xFA1E0FF3U.toInt(),
		Mnemonic.WRMSR       to 0x300F,
		Mnemonic.WRMSRLIST   to 0xC6010FF3U.toInt(),
		Mnemonic.RDTSC       to 0x310F,
		Mnemonic.RDMSR       to 0x320F,
		Mnemonic.RDMSRLIST   to 0xC6010FF2U.toInt(),
		Mnemonic.RDPMC       to 0x330F,
		Mnemonic.SYSENTER    to 0x340F,
		Mnemonic.SYSEXIT     to 0x350F,
		Mnemonic.SYSEXITQ    to 0x350F48,
		Mnemonic.GETSEC      to 0x370F,
		Mnemonic.EMMS        to 0x770F,
		Mnemonic.CPUID       to 0xA20F,
		Mnemonic.RSM         to 0xAA0F,
		Mnemonic.LFENCE      to 0xE8AE0F,
		Mnemonic.MFENCE      to 0xF0AE0F,
		Mnemonic.SFENCE      to 0xF8AE0F,
		Mnemonic.SERIALIZE   to 0xE8010F,
		Mnemonic.XSUSLDTRK   to 0xE8010FF2U.toInt(),
		Mnemonic.XRESLDTRK   to 0xE9010FF2U.toInt(),
		Mnemonic.SETSSBSY    to 0xE8010FF3U.toInt(),
		Mnemonic.SAVEPREVSSP to 0xEA010FF3U.toInt(),
		Mnemonic.UD1         to 0xB90F,
		Mnemonic.UD2         to 0x0B0F,
		Mnemonic.VZEROUPPER  to 0x77F8C5,
		Mnemonic.VZEROALL    to 0x77FCC5,
		Mnemonic.TILERELEASE to 0xC04978E2C4U.toInt()
	)

}