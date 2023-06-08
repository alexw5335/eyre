package eyre.nasm

import eyre.Op
import eyre.Width

object Maps {

	val arches     = NasmArch.values().associateBy { it.name.trimStart('_') }
	val extensions = NasmExt.values().associateBy { it.name.trimStart('_') }
	val vsibs      = VSib.values().associateBy { it.name.lowercase() }
	val immTypes   = ImmType.values().associateBy { it.name.lowercase().replace('_', ',') }
	val tupleTypes = TupleType.values().associateBy { it.name.lowercase() }
	val opEncs     = OpEnc.values().associateBy { it.string }
	
	val multiOps = mapOf(
		"rm8" to Pair(Op.R8, Op.M8),
		"rm16" to Pair(Op.R16, Op.M16),
		"rm32" to Pair(Op.R32, Op.M32),
		"rm64" to Pair(Op.R64, Op.M64),
		"mmxrm64" to Pair(Op.MM, Op.M64),
		"xmmrm8" to Pair(Op.X, Op.M8),
		"xmmrm16" to Pair(Op.X, Op.M16),
		"xmmrm32" to Pair(Op.X, Op.M32),
		"xmmrm64" to Pair(Op.X, Op.M64),
		"xmmrm128" to Pair(Op.X, Op.M128),
		"xmmrm256" to Pair(Op.X, Op.M256),
		"ymmrm16" to Pair(Op.Y, Op.M16),
		"ymmrm128" to Pair(Op.Y, Op.M128),
		"ymmrm256" to Pair(Op.Y, Op.M256),
		"zmmrm16" to Pair(Op.Z, Op.M16),
		"zmmrm128" to Pair(Op.Z, Op.M128),
		"zmmrm512" to Pair(Op.Z, Op.M512),
		"krm8" to Pair(Op.K, Op.M8),
		"krm16" to Pair(Op.K, Op.M16),
		"krm32" to Pair(Op.K, Op.M32),
		"krm64" to Pair(Op.K, Op.M64)
	)
	
	val ops = mapOf(
		"reg8" to Op.R8,
		"reg16" to Op.R16,
		"reg32" to Op.R32,
		"reg64" to Op.R64,
		"mem8" to Op.M8,
		"mem16" to Op.M16,
		"mem32" to Op.M32,
		"mem64" to Op.M64,
		"mem80" to Op.M80,
		"mem128" to Op.M128,
		"mem256" to Op.M256,
		"mem512" to Op.M512,
		"imm8" to Op.I8,
		"imm16" to Op.I16,
		"imm32" to Op.I32,
		"imm64" to Op.I64,
		"reg_al" to Op.AL,
		"reg_ax" to Op.AX,
		"reg_eax" to Op.EAX,
		"reg_rax" to Op.RAX,
		"reg_dx" to Op.DX,
		"reg_cl" to Op.CL,
		"reg_ecx" to Op.ECX,
		"reg_rcx" to Op.RCX,
		"unity" to Op.ONE,
		"fpureg" to Op.ST,
		"fpu0" to Op.ST0,
		"mmxreg" to Op.MM,
		"xmmreg" to Op.X,
		"ymmreg" to Op.Y,
		"zmmreg" to Op.Z,
		"xmem32" to Op.VM32X,
		"xmem64" to Op.VM64X,
		"ymem32" to Op.VM32Y,
		"ymem64" to Op.VM64Y,
		"zmem32" to Op.VM32Z,
		"zmem64" to Op.VM64Z,
		"kreg" to Op.K,
		"bndreg" to Op.BND,
		"tmmreg" to Op.T,
		"reg_sreg" to Op.SEG,
		"reg_creg" to Op.CR,
		"reg_dreg" to Op.DR,
		"reg_fs" to Op.FS,
		"reg_gs" to Op.GS,
	)
	
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
		"iwdq",
		"a64",
		"o32",
		"o64nw"
	)

}