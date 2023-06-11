package eyre

import eyre.gen.ManualParser
import eyre.gen.NasmExt
import eyre.util.Unique


fun main() {
	val manualParser = ManualParser("encodings.txt")
	val manualEncodings = manualParser.parse()

	/*

	A_MOF 1
	M_SEG 1
	RA 1
	I8_A 1
	MM_MMM 1
	A_DX 1
	O_A 1
	O_I 1
	R_CR 1
	R_RM_I 1
	MM_X 1
	R_SEG 1
	I32 1
	SEG_M 1
	DX_A 1
	SEG_R 1
	MM_RM_I8 1
	R_MM 1
	XM32_X 1
	E_EM_I8 1
	CR_R 1
	R_M 1
	MMM_MM 1
	R_DR 1
	DR_R 1
	MOF_A 1
	X_MM 1
	A_O 1
	A_I8 1
	R32_RM 1
	R_MM_I8 1
	M64_MM 1
	R_RM_I8 1
	MM_MMM_I8 1
	R_RM32 1
	M128 1
	MM_MM 1
	XM64_X 2
	R_XM64 2
	R_RM16 2
	RM_R_I8 2
	AX 2
	X_MMM 2
	RM_X_I8 2
	MM_RM 2
	RM_X 2
	RM_R_CL 2
	X_I8 2
	RM_MM 2
	MM_XM 2
	I16_I8 2
	R_REG 2
	R_XM32 2
	MM_XM64 2
	X_XM64_I8 2
	X_M128 2
	X_XM16 2
	M128_X 3
	O 3
	R_RM8 3
	R_X 3
	RA_M512 3
	X_XM32_I8 3
	M_X_I8 3
	R64_M128 3
	M80 4
	X_RM_I8 4
	I8 4
	REL32 4
	X_M64 4
	R_X_I8 4
	X_XM_X0 4
	X_RM 4
	M64_X 4
	FS 4
	GS 4
	REL8 4
	I16 5
	XM_X 6
	R_MEM 6
	M_R 7
	RM_1 8
	RM_CL 8
	E_I8 8
	A_I 9
	RM_I 10
	ST_ST0 13
	M64 15
	X_XM32 17
	M16 17
	R 17
	RM_R 18
	RM_I8 20
	RM 21
	M 21
	X_XM64 21
	R_RM 23
	ST0_ST 23
	X_XM_I8 24
	M32 25
	MEM 29
	ST 33
	X_X 39
	E_EM 70
	X_XM 76
	NONE 199

	 */

/*	val nasmMap = HashMap<String, ArrayList<CommonEncoding>>()
	val manualMap = HashMap<String, ArrayList<CommonEncoding>>()
	for(e in nasmEncodings) nasmMap.getOrPut(e.mnemonic, ::ArrayList).add(e)
	for(e in manualEncodings) manualMap.getOrPut(e.mnemonic, ::ArrayList).add(e)

	manualEncodings
		.map { it.mnemonic }
		.toSet()
		.sorted()
		.forEach { println("$it,") }

	for((mnemonic, manual) in manualMap) {
		//if(mnemonic in ignoredMnemonics) continue
		val nasm = nasmMap[mnemonic] ?: error("Missing mnemonic: $mnemonic")

		for(encoding in manual) {
			if(encoding.pseudo >= 0)
				continue
			if(nasm.none { it == encoding }) {
				println(encoding)
				for(m in nasm)
					println("\t$m")
			}
		}
	}*/



/*	val nasmEncodings = NasmParser("nasm.txt", extensions).let { it.read(); it.encodings }
	val manualEncodings = ManualParser("encodings.txt").let { it.parse(); it.encodings }

	val nasmMap = HashMap<String, ArrayList<Encoding>>()
	val manualMap = HashMap<String, ArrayList<Encoding>>()
	for(e in nasmEncodings) nasmMap.getOrPut(e.mnemonic, ::ArrayList).add(e)
	for(e in manualEncodings) manualMap.getOrPut(e.mnemonic, ::ArrayList).add(e)

	manualEncodings
		.map(Encoding::mnemonic)
		.toSet()
		.sorted()
		.forEach { println("$it,")}*/

/*	for((mnemonic, manual) in manualMap) {
		if(mnemonic in ignoredMnemonics) continue
		val nasm = nasmMap[mnemonic] ?: error("Missing mnemonic: $mnemonic")

		for(encoding in manual) {
			if(encoding.pseudo >= 0)
				continue
			if(nasm.none { it == encoding }) {
				println(encoding)
				for(m in nasm)
					println("\t$m")
			}
		}
	}*/

/*	for((mnemonic, nasm) in nasmMap) {
		val manual = manualMap[mnemonic] ?: error("Missing mnemonic: $mnemonic")

		for(encoding in nasm) {
			if(encoding.pseudo >= 0)
				continue
			if(encoding.ops.size == 1 && encoding.ops[0] == Op.MEM)
				if(manual.count { it.ops.size == 1 && it.ops[0].type == OpType.M } == 1)
					continue
			when(encoding.mnemonic) {
				"LSL", "MOV", "UD1", "BNDMOV", "BNDCN", "BNDCL", "BNDCU", "BNDLDX", "BNDSTX",
				"JMP", "JECXZ", "PUSH", "XBEGIN", "BNDMK", "LOOP", "LOOPE", "LOOPZ", "LOOPNZ",
				"LOOPNE", "LAR", "CALL", "TEST"
				-> continue
			}
			if(manual.none { it == encoding }) {
				println(encoding)
				for(m in manual)
					println("\t$m")
			}
		}

	}*/
}



private val extensions = setOf(
	// General-purpose
	NasmExt.CET, NasmExt.FPU, NasmExt.ENQCMD, NasmExt.HRESET,
	NasmExt.INVPCID, NasmExt.MPX, NasmExt.PCONFIG, NasmExt.PREFETCHI,
	NasmExt.PREFETCHWT1, NasmExt.RTM, NasmExt.SERIALIZE, NasmExt.SGX,
	NasmExt.TSXLDTRK, NasmExt.UINTR, NasmExt.VMX, NasmExt.WBNOINVD,
	NasmExt.WRMSRNS,
	// MMX/SSE
	NasmExt.MMX, NasmExt.SSE, NasmExt.SSE2, NasmExt.SSE3, NasmExt.SSE41,
	NasmExt.SSE42, NasmExt.SSE4A, NasmExt.SSE5, NasmExt.SSSE3
)



private val ignoredMnemonics = setOf(
	// Custom opcodes
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
	"GF2P8MULB",
	"GF2P8AFFINEINVQB",
	"GF2P8AFFINEQB",
	"SHA256RNDS2",
	"SHA1NEXTE",
	"PREFETCHW",
	"TZCNT",
	"SAL",
	"SHA256MSG1",
	"SHA256MSG2",
	"SHA1MSG2",
	"SHA1MSG1",
	"SHA1RNDS4",

	// ?
	"BNDCN",
	"BNDCL",
	"BNDCU",
	"BNDMK",
	"MOV",
	"BNDMOV",
	"MOVSXD",


	// Manual: X_MEM    NASM: X_M128
	"LDDQU",
	// Manual: M64    NASM: MEM
	"VMPTRLD",
	"VMCLEAR",
	"VMPTRST",
	"VMXON",
	// EAX explicit second operand
	"HRESET",
	// MEM/REG conversion issues
	"LSL",
	"LAR",
	// Manual: M64    NASM: M128
	"CMPSD",
	// Manual: M8    NASM: MEM
	"CLDEMOTE",
	"CLFLUSHOPT",
	"CLFLUSH",
	"CLWB",
	// Manual: M64 and M128    NASM: MEM
	"PALIGNR",
	// Empty encodings
	"LOOPNZ",
	"LOOPNE",
	"LOOPZ",
	"LOOPE",
	"LOOP",
	// A32 prefix
	"JECXZ",
	// Messy encodings
	"PEXTRW"

)