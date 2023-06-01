package eyre.nasm

import eyre.Width

object Maps {

	val arches     = NasmArch.values().associateBy { it.name.trimStart('_') }
	val extensions = NasmExt.values().associateBy { it.name.trimStart('_') }
	val opParts    = OpPart.values().associateBy { it.name.lowercase().replace('_', ',') }
	val vsibs      = VSib.values().associateBy { it.name.lowercase() }
	val operands   = NasmOp.values().associateBy { it.string }
	val immTypes   = ImmType.values().associateBy { it.name.lowercase().replace('_', ',') }
	val tupleTypes = TupleType.values().associateBy { it.name.lowercase() }
	val opEncs     = OpEnc.values().associateBy { it.string }

	val ccList = arrayOf(
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
		"NLE" to 15, "JG" to 15
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

	val invalidMnemonics = setOf(
		// Two immediates
		"ENTER",
		// Obsolete
		"JMPE",
		// Not found in Intel manuals
		"CMPccXADD",
		// Unnecessary size specifiers
		"RETN",
		"RETQ",
		"RETNW",
		"RETNQ",
		"RETFW",
		"RETFD"
	)

	val essentialMnemonics = setOf(
		"SYSCALL",
		"SYSRET",
		"LZCNT",
		"PRETETCHW"
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
		"iwdq"
	)

}