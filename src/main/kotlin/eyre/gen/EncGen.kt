package eyre.gen

import eyre.*
import eyre.util.hexc8

object EncGen {


	val nasmParser = NasmParser("nasm.txt", true, null)

	val manualParser = ManualParser("encodings.txt")

	val nasmLines get() = nasmParser.lines

	val nasmEncs get() = nasmParser.commonEncs

	val manualEncs get() = manualParser.commonEncs

	val nasmMap = HashMap<String, ArrayList<CommonEnc>>()

	val manualMap = HashMap<String, ArrayList<CommonEnc>>()



	init {
		nasmParser.read()
		manualParser.read()
		for(e in nasmEncs) nasmMap.getOrPut(e.mnemonic, ::ArrayList).add(e)
		for(e in manualEncs) manualMap.getOrPut(e.mnemonic, ::ArrayList).add(e)
	}



	fun genSseEncs2() {
		val encs = sseEncs()
		val map = HashMap<String, ArrayList<Int>>()


		for(e in encs) {
			var width = Width.BYTE

			fun Op.toSseOp() = when(this) {
				Op.R8 -> SseOp.R8
				Op.R16 -> SseOp.R16
				Op.R32 -> SseOp.R32
				Op.R64 -> SseOp.R64
				Op.MM -> SseOp.MM
				Op.X -> SseOp.X
				Op.I8 -> SseOp.NONE
				Op.M8 -> { width = Width.BYTE; SseOp.M }
				Op.M16 -> { width = Width.WORD; SseOp.M }
				Op.M32 -> { width = Width.DWORD; SseOp.M }
				Op.M64 -> { width = Width.QWORD; SseOp.M }
				Op.M128 -> { width = Width.XWORD; SseOp.M }
				else -> error("Invalid SSE operand: $this")
			}

			val sseOps = when(e.ops.size) {
				0 -> SseOps(false, SseOp.NONE, SseOp.NONE)
				2 -> SseOps(e.ops[1] == Op.I8, e.ops[0].toSseOp(), e.ops[1].toSseOp())
				3 -> SseOps(e.ops[2] == Op.I8, e.ops[0].toSseOp(), e.ops[1].toSseOp())
				else -> error("Invalid SSE encoding: $this")
			}

			val sseEnc = SseEnc(
				e.opcode,
				e.prefix.ordinal,
				e.escape.ordinal,
				e.ext.coerceAtLeast(0),
				sseOps,
				width,
				e.rexw,
				e.o16,
				if(e.mr) 1 else 0
			)

			map.getOrPut(e.mnemonic, ::ArrayList).add(sseEnc.value)
		}

		println("val sseEncs = mapOf<Mnemonic, IntArray>(")
		for((mnemonic, values) in map) {
			println("\t$mnemonic to intArrayOf(${values.joinToString()}),")
		}
		println(")")
	}



	private fun sseEncs() = buildList {
		val mnemonics = HashSet<String>()
		for(n in nasmEncs)
			if(!n.isAvx && (n.ops.contains(Op.X) || n.ops.contains(Op.MM)))
				mnemonics += n.mnemonic
		for(n in nasmEncs)
			if(n.mnemonic in mnemonics)
				add(n)
	}.sortedBy(CommonEnc::mnemonic)



	fun genSseEncs() {
		val encs = sseEncs()

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