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
		// Obsolete
		"JMPE",
		// Not found in Intel manuals?
		"CMPccXADD",
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