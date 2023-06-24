package eyre.gen

import eyre.Escape
import eyre.Prefix
import eyre.util.hexc8

fun main() {
	Encs.genSse()
}


object Encs {


	private val nasmParser = NasmParser("nasm.txt", true, null)

	private val manualParser = ManualParser("encodings.txt")

	private val nasmLines get() = nasmParser.lines

	private val nasmEncs get() = nasmParser.commonEncs

	private val manualEncs get() = manualParser.commonEncs

	private val nasmMap = HashMap<String, ArrayList<CommonEnc>>()

	private val manualMap = HashMap<String, ArrayList<CommonEnc>>()



	init {
		nasmParser.read()
		manualParser.read()
		for(e in nasmEncs) nasmMap.getOrPut(e.mnemonic, ::ArrayList).add(e)
		for(e in manualEncs) manualMap.getOrPut(e.mnemonic, ::ArrayList).add(e)
	}



	fun genSse() {
		val mnemonics = HashSet<String>()
		nasmEncs
			.filter { !it.isAvx && (it.ops.contains(Op.X) || it.ops.contains(Op.MM)) }
			.forEach { mnemonics += it.mnemonic }
		val encs = nasmEncs
			.filter { it.mnemonic in mnemonics }
			.sortedBy { it.mnemonic }

		for(e in encs) {
			when(e.mnemonic) {
				"CRC32",
				"EMMS",
				"LFENCE",
				"MFENCE",
				"LDMXCSR",
				"STMXCSR",
				"CLFLUSH" -> continue
			}

			buildString {
				val list = ArrayList<String>()
				if(e.prefix != Prefix.NONE) list += e.prefix.value.hexc8
				when(e.escape) {
					Escape.NONE -> { }
					Escape.E0F -> list += "0F"
					Escape.E38 -> { list += "0F"; list += "38" }
					Escape.E3A -> { list += "0F"; list += "3A" }
				}
				if(e.opcode and 0xFF00 != 0) error("Invalid opcode: $e")
				list += e.opcode.hexc8
				append(list.joinToString(" "))
				if(e.ext >= 0) append("/${e.ext}")
				append("  ")
				append(e.mnemonic)
				append("  ")
				append(e.ops.joinToString("_"))
				append(" ")
				if(e.mr) append(" MR")
				if(e.o16 == 1) append(" O16")
				if(e.rexw == 1) append(" RW")
				if(e.pseudo >= 0) append(" :${e.pseudo}")
			}.let(::println)
		}
	}



	private fun compare(nasm: CommonEnc, manual: CommonEnc): Boolean {
		if(nasm.ops.singleOrNull() == Op.MEM && manual.ops.size == 1 && manual.ops[0].type == OpType.M)
			return nasm.equals(manual, true)
		return nasm.equals(manual, false)
	}



	fun compareNasmToManual() {
		println("Comparing NASM -> Manual")
		for((mnemonic, nasms) in nasmMap) {
			if(nasms.any { it.isAvx }) continue
			if(mnemonic in nasmToManualIgnoredMnemonics) continue
			val manuals = manualMap[mnemonic] ?: error("Missing mnemonic: $mnemonic")
			for(nasm in nasms) {
				if(manuals.any { compare(nasm, it) }) continue
				println(nasm)
				for(manual in manuals)
					println("\t$manual")
			}
		}
	}



	fun compareManualToNasm() {
		println("Comparing Manual -> NASM")
		for((mnemonic, manuals) in manualMap) {
			if(mnemonic in manualToNasmIgnoredMnemonics) continue
			val nasms = nasmMap[mnemonic] ?: error("Missing mnemonic: $mnemonic")
			outer@ for(manual in manuals) {
				if(nasms.any { compare(it, manual) }) continue
				println(manual)
				for(nasm in nasms)
					println("\t$nasm")
			}
		}
	}



	/** NASM -> Manual ignored mnemonics */
	private val nasmToManualIgnoredMnemonics = setOf(
		// Custom
		"IN", "OUT", "MOV", "ENTER", "XCHG", "LAR", "LSL",
		// Explicit ECX and RCX
		"LOOPNZ", "LOOPNE", "LOOPZ", "LOOPE", "LOOP",
		// Explicit EAX
		"HRESET",
		// A32 prefix
		"JECXZ",
		// Issues with SseOps and ops conversion
		"MOVSD", "CMPSD",
		// NASM gives opcode as 0 and prefix as 9B
		"FWAIT",
	)



	/** Manual -> NASM ignored mnemonics */
	private val manualToNasmIgnoredMnemonics = nasmToManualIgnoredMnemonics + setOf(
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
		"AOR",
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
	)



	private val gpExtensions = setOf(
		NasmExt.CET, NasmExt.FPU, NasmExt.ENQCMD, NasmExt.HRESET,
		NasmExt.INVPCID, NasmExt.MPX, NasmExt.PCONFIG, NasmExt.PREFETCHI,
		NasmExt.PREFETCHWT1, NasmExt.RTM, NasmExt.SERIALIZE, NasmExt.SGX,
		NasmExt.TSXLDTRK, NasmExt.UINTR, NasmExt.VMX, NasmExt.WBNOINVD,
		NasmExt.WRMSRNS,
	)

	private val mmxSseExtensions = setOf(
		NasmExt.MMX, NasmExt.SSE, NasmExt.SSE2, NasmExt.SSE3, NasmExt.SSE41,
		NasmExt.SSE42, NasmExt.SSE4A, NasmExt.SSE5, NasmExt.SSSE3, NasmExt.SHA
	)

	private val avxExtensions = setOf(
		NasmExt.AVX,
		NasmExt.AVX2
	)


}