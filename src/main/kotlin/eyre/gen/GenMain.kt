package eyre.gen

import eyre.util.hexc16
import eyre.util.hexc24
import eyre.util.hexc32
import eyre.util.hexc8



fun main() {
	//compare()
	//genSse()
	genEncodings0()
}


private fun genEncodings0() {
	val encodings = ManualParser("encodings.txt").let { it.read(); it.encodings }
	for(e in encodings) {
		if(e.ops == Ops.NONE && e.sseOps == SseOps.NULL) {
			val values = ArrayList<Int>()
			if(e.o16 == 1) values += 0x66
			if(e.prefix != Prefix.NONE) values += e.prefix.value
			if(e.rexw == 1) values += 0x48
			when(e.escape) {
				Escape.NONE -> { }
				Escape.E0F -> values += 0x0F
				Escape.E38 -> { values += 0x0F; values += 0x38 }
				Escape.E3A -> { values += 0x0F; values += 0x3A }
				Escape.E00 -> { values += 0x00 }
			}
			values += e.opcode and 0xFF
			if(e.opcode and 0xFF00 != 0) values += e.opcode shr 8
			print(e.mnemonic)
			print(" -> ")
			var value = 0
			for(i in values.indices)
				value = value or (values[i] shl (i shl 3))
			when(values.size) {
				1 -> print("writer.i8(0x${value.hexc8})")
				2 -> print("writer.i16(0x${value.hexc16})")
				3 -> print("writer.i24(0x${value.hexc24})")
				4 -> print("writer.i32(0x${value.hexc32})")
				else -> error("Invalid opcode")
			}
			println()
		}
	}
}


private fun genSse() {
	val encodings = NasmParser("nasm.txt", false, mmxSseExtensions)
		.let { it.read(); it.encodings }
		.sortedBy { it.mnemonic }

	for(e in encodings) {
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
				Escape.E00 -> list += "00"
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



private fun compare() {
	val manualParser = ManualParser("encodings.txt").also { it.read()}
	val manualEncodings = manualParser.commonEncodings
	val nasmParser = NasmParser("nasm.txt", true, gpExtensions + mmxSseExtensions)
	nasmParser.read()
	val nasmEncodings = nasmParser.encodings

	val manualMap = HashMap<String, ArrayList<CommonEncoding>>()
	val nasmMap = HashMap<String, ArrayList<CommonEncoding>>()

	for(e in manualEncodings) manualMap.getOrPut(e.mnemonic, ::ArrayList).add(e)
	for(e in nasmEncodings) nasmMap.getOrPut(e.mnemonic, ::ArrayList).add(e)

	println("Comparing NASM -> Manual")
	for((mnemonic, nasms) in nasmMap) {
		if(mnemonic in ignored) continue
		val manuals = manualMap[mnemonic] ?: error("Missing mnemonic: $mnemonic")

		outer@ for(nasm in nasms) {
			for(manual in manuals) {
				when {
					manual == nasm
					-> continue@outer
					nasm.ops.size == 1 && nasm.ops[0] == Op.MEM && manual.ops.size == 1 && manual.ops[0].type == OpType.M
					-> continue@outer
				}
			}
			println(nasm)
			for(manual in manuals)
				println("\t$manual")
		}
	}

	println("\nComparing Manual -> NASM")
	for((mnemonic, manuals) in manualMap) {
		if(mnemonic in ignored2) continue
		val nasms = nasmMap[mnemonic] ?: error("Missing mnemonic: $mnemonic")

		outer@ for(manual in manuals) {
			for(nasm in nasms) {
				when {
					manual == nasm
					-> continue@outer
					nasm.ops.size == 1 && nasm.ops[0] == Op.MEM && manual.ops.size == 1 && manual.ops[0].type == OpType.M
					-> continue@outer
				}
			}
			println(manual)
			for(nasm in nasms)
				println("\t$nasm")
		}
	}
}



private val gpExtensions = setOf(
	NasmExt.CET, NasmExt.FPU, NasmExt.ENQCMD, NasmExt.HRESET,
	NasmExt.INVPCID, NasmExt.MPX, NasmExt.PCONFIG, NasmExt.PREFETCHI,
	NasmExt.PREFETCHWT1, NasmExt.RTM, NasmExt.SERIALIZE, NasmExt.SGX,
	NasmExt.TSXLDTRK, NasmExt.UINTR, NasmExt.VMX, NasmExt.WBNOINVD,
	NasmExt.WRMSRNS,
)



private val mmxSseExtensions = setOf(
	NasmExt.MMX, NasmExt.SSE, NasmExt.SSE2, NasmExt.SSE3, NasmExt.SSE41,
	NasmExt.SSE42, NasmExt.SSE4A, NasmExt.SSE5, NasmExt.SSSE3
)



// NASM to manual
private val ignored = setOf(
	// Custom
	"IN", "OUT", "MOV", "ENTER", "XCHG", "LAR", "LSL",
	// Explicit ECX and RCX
	"LOOPNZ", "LOOPNE", "LOOPZ", "LOOPE", "LOOP",
	// Explicit EAX
	"HRESET",
	// A32 prefix
	"JECXZ",
	// Issues with SseOps and ops conversion
	"MOVSD", "CMPSD"
)



// Manual to NASM
private val ignored2 = ignored + setOf(
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
)



/*private val ignoredMnemonics = setOf(
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
)*/