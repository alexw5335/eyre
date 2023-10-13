package eyre.gen

import eyre.OpEnc
import eyre.OpType
import eyre.Width

object NasmLists {

	val arches     = NasmArch.entries.associateBy { it.name.trimStart('_') }
	val extensions = NasmExt.entries.associateBy { it.name.trimStart('_') }
	val vsibs      = NasmVsib.entries.associateBy { it.name.lowercase() }
	val immTypes   = NasmImm.entries.associateBy { it.name.lowercase().replace('_', ',') }
	val tupleTypes = NasmTuple.entries.associateBy { it.name.lowercase() }
	val opEncs     = NasmOpEnc.entries.associateBy { it.string }
	val ops        = NasmOp.entries.associateBy { it.nasmString }

	val mrEncs = setOf(NasmOpEnc.MR, NasmOpEnc.MRN, NasmOpEnc.MRX, NasmOpEnc.MRI)

	val opEncConversionMap = mapOf(
		NasmOpEnc.R to OpEnc.RMV,
		NasmOpEnc.RI to OpEnc.RMV,
		NasmOpEnc.RM to OpEnc.RMV,
		NasmOpEnc.RMV to OpEnc.RMV,
		NasmOpEnc.RMI to OpEnc.RMV,
		NasmOpEnc.RMVI to OpEnc.RMV,
		NasmOpEnc.RVM to OpEnc.RVM,
		NasmOpEnc.RVMI to OpEnc.RVM,
		NasmOpEnc.RVMS to OpEnc.RVM,
		NasmOpEnc.M to OpEnc.MRV,
		NasmOpEnc.MI to OpEnc.MRV,
		NasmOpEnc.MR to OpEnc.MRV,
		NasmOpEnc.MRI to OpEnc.MRV,
		NasmOpEnc.MRN to OpEnc.MRV,
		NasmOpEnc.MRV to OpEnc.MRV,
		NasmOpEnc.MVR to OpEnc.MVR,
		NasmOpEnc.VM to OpEnc.VMR,
		NasmOpEnc.VMI to OpEnc.VMR
	)

	val opTypeConversionMap = mapOf(
		NasmOp.NONE  to OpType.NONE,
		NasmOp.MEM   to OpType.MEM,
		NasmOp.M8    to OpType.MEM,
		NasmOp.M16   to OpType.MEM,
		NasmOp.M32   to OpType.MEM,
		NasmOp.M64   to OpType.MEM,
		NasmOp.M80   to OpType.MEM,
		NasmOp.M128  to OpType.MEM,
		NasmOp.M256  to OpType.MEM,
		NasmOp.M512  to OpType.MEM,
		NasmOp.VM32X to OpType.MEM,
		NasmOp.VM64X to OpType.MEM,
		NasmOp.VM32Y to OpType.MEM,
		NasmOp.VM64Y to OpType.MEM,
		NasmOp.VM32Z to OpType.MEM,
		NasmOp.VM64Z to OpType.MEM,
		NasmOp.I8    to OpType.IMM,
		NasmOp.I16   to OpType.IMM,
		NasmOp.I32   to OpType.IMM,
		NasmOp.I64   to OpType.IMM,
		NasmOp.R8    to OpType.R8,
		NasmOp.R16   to OpType.R16,
		NasmOp.R32   to OpType.R32,
		NasmOp.R64   to OpType.R64,
		NasmOp.MM    to OpType.MM,
		NasmOp.X     to OpType.X,
		NasmOp.Y     to OpType.Y,
		NasmOp.Z     to OpType.Z,
		NasmOp.K     to OpType.K,
		NasmOp.T     to OpType.T,
		NasmOp.ST    to OpType.ST,
		NasmOp.AX    to OpType.R16,
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


}