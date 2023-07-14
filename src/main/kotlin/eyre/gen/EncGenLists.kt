package eyre.gen

import eyre.Mnemonic
import eyre.Width

object EncGenLists {

	val arches     = NasmArch.entries.associateBy { it.name.trimStart('_') }
	val extensions = NasmExt.entries.associateBy { it.name.trimStart('_') }
	val vsibs      = VSib.entries.associateBy { it.name.lowercase() }
	val immTypes   = ImmType.entries.associateBy { it.name.lowercase().replace('_', ',') }
	val tupleTypes = TupleType.entries.associateBy { it.name.lowercase() }
	val opEncs     = OpEnc.entries.associateBy { it.string }
	val ops        = Op.entries.associateBy { it.nasmString }
	val mnemonics  = Mnemonic.entries.associateBy { it.name }

	val mrEncs = setOf(OpEnc.MR, OpEnc.MRN, OpEnc.MRX, OpEnc.MRI)

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
		"P" to 9, "PE" to 10,
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
		// Two immediates
		"ENTER",
		// Obsolete
		"JMPE",
		// Not found in Intel manual
		"CMPccXADD",
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
		// BND
		"BNDMK",
		"BNDCL",
		"BNDCU",
		"BNDCU",
		"BNDCN",
		"BNDMOV",
		"BNDLDX",
		"BNDSTX",
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

}