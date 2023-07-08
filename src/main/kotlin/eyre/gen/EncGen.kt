package eyre.gen

import eyre.*
import eyre.util.hexc8
import java.nio.file.Files
import java.nio.file.Paths

object EncGen {


	private val nasmParser = NasmParser(Files.readAllLines(Paths.get("nasm.txt")))

	private val nasmEncs get() = nasmParser.encs

	private val nasmMap = HashMap<String, ArrayList<NasmEnc>>()



	init {
		nasmParser.read()
		for(e in nasmEncs)
			nasmMap.getOrPut(e.mnemonic, ::ArrayList).add(e)
	}



	fun run() {
		genMnemonics()
	}




	/*
	Mnemonic generation
	 */



	private fun genMnemonics() {
		val mnemonics = HashMap<String, Mnemonic.Type>()

		for(e in nasmEncs) {
			when {
				e.isAvx ->
					mnemonics[e.mnemonic] = Mnemonic.Type.AVX
				Op.X in e.ops || Op.MM in e.ops ->
					if(mnemonics[e.mnemonic] != Mnemonic.Type.AVX)
						mnemonics[e.mnemonic] = Mnemonic.Type.SSE
				else ->
					if(mnemonics[e.mnemonic] == null)
						mnemonics[e.mnemonic] = Mnemonic.Type.GP
			}
		}

		for(m in EncGenLists.pseudoMnemonics)
			println("$m(Type.PSEUDO),")
		for(m in EncGenLists.additionalMnemonics)
			println("$m(Type.GP),")
		for((mnemonic, type) in mnemonics.entries.sortedBy { it.key })
			println("$mnemonic(Type.$type),")
	}



	/*
	MMX/SSE instruction generation
	 */



	/**
	 * Generates a mapping of mnemonics to lists of non-human-readable [SseEnc] values for use in code.
	 */
	fun genSseEncMap() {
		val encs = sseEncs()
		val map = HashMap<String, ArrayList<SseEnc>>()

		fun Op?.toSseOp() = when(this) {
			null    -> SseOp.NONE
			Op.NONE -> SseOp.NONE
			Op.I8   -> SseOp.NONE
			Op.R8   -> SseOp.R8
			Op.R16  -> SseOp.R16
			Op.R32  -> SseOp.R32
			Op.R64  -> SseOp.R64
			Op.MM   -> SseOp.MM
			Op.X    -> SseOp.X
			Op.M8   -> SseOp.M8
			Op.M16  -> SseOp.M16
			Op.M32  -> SseOp.M32
			Op.M64  -> SseOp.M64
			Op.M128 -> SseOp.M128
			else    -> error("Invalid SSE operand: $this")
		}

		for(e in encs) {
			val op1 = e.ops.getOrNull(0).toSseOp()
			val op2 = e.ops.getOrNull(1).toSseOp()
			val i8 = when(e.ops.size) {
				2    -> e.ops[1] == Op.I8
				3    -> e.ops[2] == Op.I8
				else -> false
			}

			val sseEnc = SseEnc(
				e.opcode,
				e.prefix.ordinal,
				e.escape.ordinal,
				e.ext.coerceAtLeast(0),
				op1,
				op2,
				if(i8) 1 else 0,
				e.rexw,
				e.o16,
				if(e.mr) 1 else 0
			)

			map.getOrPut(e.mnemonic, ::ArrayList).add(sseEnc)
		}

		val toAdd = ArrayList<SseEnc>()

		for((_, list) in map) {
			for(e in list)
				if(e.op1.isM || e.op2.isM)
					if(list.none { it.equalExceptMem(e) })
						toAdd += e.withoutMemWidth()
			if(toAdd.isNotEmpty()) {
				list += toAdd
				toAdd.clear()
			}
		}

		println("val sseEncs = mapOf<Mnemonic, IntArray>(")
		for((mnemonic, values) in map)
			println("\tMnemonic.$mnemonic to intArrayOf(${values.joinToString { it.value.toString() }}),")
		println(")")
	}



	/**
	 * Gathers the instructions of all mnemonics that contain any MMX/SSE operands
	 */
	private fun sseEncs() = buildList {
		val mnemonics = HashSet<String>()
		for(n in nasmEncs)
			if(!n.isAvx && (n.ops.contains(Op.X) || n.ops.contains(Op.MM)))
				mnemonics += n.mnemonic
		for(n in nasmEncs)
			if(n.mnemonic in mnemonics)
				add(n)
	}.sortedBy(NasmEnc::mnemonic)



	/**
	 * Generates an expanded human-readable list of all MMX/SSE instructions.
	 */
	fun genSseEncList() {
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



	/*
	Collections
	 */



	/**
	 * NASM -> Manual ignored mnemonics
	 */
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



	/**
	 * Manual -> NASM ignored mnemonics
	 */
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



	/**
	 * NASM extensions that only contain general-purpose operands
	 */
	private val gpExtensions = setOf(
		NasmExt.CET, NasmExt.FPU, NasmExt.ENQCMD, NasmExt.HRESET,
		NasmExt.INVPCID, NasmExt.MPX, NasmExt.PCONFIG, NasmExt.PREFETCHI,
		NasmExt.PREFETCHWT1, NasmExt.RTM, NasmExt.SERIALIZE, NasmExt.SGX,
		NasmExt.TSXLDTRK, NasmExt.UINTR, NasmExt.VMX, NasmExt.WBNOINVD,
		NasmExt.WRMSRNS,
	)



	/**
	 * NASM extensions that contain any MMX or XMM operands.
	 */
	private val mmxSseExtensions = setOf(
		NasmExt.MMX, NasmExt.SSE, NasmExt.SSE2, NasmExt.SSE3, NasmExt.SSE41,
		NasmExt.SSE42, NasmExt.SSE4A, NasmExt.SSE5, NasmExt.SSSE3, NasmExt.SHA
	)



	/**
	 * NASM extensions that contain any AVX or AVX-512 operands.
	 */
	private val avxExtensions = setOf(
		NasmExt.AVX,
		NasmExt.AVX2
	)


}