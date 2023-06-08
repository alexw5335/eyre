package eyre

import eyre.manual.ManualParser
import eyre.nasm.NasmExt
import eyre.nasm.NasmParser



fun main() {
	val nasmEncodings = NasmParser("nasm.txt", extensions).let { it.read(); it.encodings }
	val manualEncodings = ManualParser("encodings.txt").let { it.read(); it.encodings }

	val nasmMap = HashMap<String, ArrayList<Encoding>>()
	val manualMap = HashMap<String, ArrayList<Encoding>>()
	for(e in nasmEncodings) nasmMap.getOrPut(e.mnemonic, ::ArrayList).add(e)
	for(e in manualEncodings) manualMap.getOrPut(e.mnemonic, ::ArrayList).add(e)

	manualEncodings
		.map(Encoding::mnemonic)
		.toSet()
		.sorted()
		.forEach { println("$it,")}

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