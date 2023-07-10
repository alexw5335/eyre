package eyre.gen

import eyre.*
import eyre.util.hexc8
import java.nio.file.Files
import java.nio.file.Paths

object EncGen {


	private val nasmParser = NasmParser(Files.readAllLines(Paths.get("nasm.txt")))

	private val nasmEncs: List<NasmEnc>

	private val nasmMap = HashMap<String, ArrayList<NasmEnc>>()

	private val mnemonicsByName = Mnemonic.entries.associateBy { it.name }



	init {
		nasmParser.read()
		nasmEncs = nasmParser.encs.sortedBy { it.mnemonic }
		for(e in nasmEncs)
			nasmMap.getOrPut(e.mnemonic, ::ArrayList).add(e)
	}



	fun run() {
		test()
	}



	private fun encsOfType(type: Mnemonic.Type) = nasmEncs
		.filter { mnemonicsByName[it.mnemonic]!!.type == type }



	/*
	Testing
	 */



	private fun Op?.toOpNode(rex: Boolean): OpNode? = when(this) {
		null -> null
		Op.NONE -> null
		Op.R8 -> OpNode.reg(if(rex) Reg.R14B else Reg.CL)
		Op.R16 -> OpNode.reg(if(rex) Reg.R14W else Reg.CX)
		Op.R32 -> OpNode.reg(if(rex) Reg.R14D else Reg.ECX)
		Op.R64 -> OpNode.reg(if(rex) Reg.R14 else Reg.RCX)
		Op.MEM -> OpNode.mem(null, RegNode(Reg.R14))
		Op.M8 -> OpNode.mem(Width.BYTE, RegNode(Reg.R14))
		Op.M16 -> OpNode.mem(Width.WORD, RegNode(Reg.R14))
		Op.M32 -> OpNode.mem(Width.DWORD, RegNode(Reg.R14))
		Op.M64 -> OpNode.mem(Width.QWORD, RegNode(Reg.R14))
		Op.M80 -> OpNode.mem(Width.TWORD, RegNode(Reg.R14))
		Op.M128 -> OpNode.mem(Width.XWORD, RegNode(Reg.R14))
		Op.M256 -> OpNode.mem(Width.YWORD, RegNode(Reg.R14))
		Op.M512 -> OpNode.mem(Width.ZWORD, RegNode(Reg.R14))
		Op.I8 -> OpNode.imm(null, IntNode(10))
		Op.I16 -> OpNode.imm(null, IntNode(10))
		Op.I32 -> OpNode.imm(null, IntNode(10))
		Op.I64 -> OpNode.imm(null, IntNode(10))
		Op.AL -> OpNode.reg(Reg.AL)
		Op.AX -> OpNode.reg(Reg.AX)
		Op.EAX -> OpNode.reg(Reg.EAX)
		Op.RAX -> OpNode.reg(Reg.RAX)
		Op.CL -> OpNode.reg(Reg.CL)
		Op.ECX -> OpNode.reg(Reg.ECX)
		Op.RCX -> OpNode.reg(Reg.RCX)
		Op.DX -> OpNode.reg(Reg.DX)
		Op.REL8 -> OpNode.imm(null, IntNode(10))
		Op.REL16 -> OpNode.imm(null, IntNode(10))
		Op.REL32 -> OpNode.imm(null, IntNode(10))
		Op.ST -> OpNode.reg(Reg.ST3)
		Op.ST0 -> OpNode.reg(Reg.ST0)
		Op.ONE -> OpNode.imm(null, IntNode(1))
		Op.MM -> OpNode.reg(Reg.MM3)
		Op.X -> OpNode.reg(if(rex) Reg.XMM14 else Reg.XMM3)
		Op.Y -> OpNode.reg(if(rex) Reg.YMM14 else Reg.YMM3)
		Op.Z -> OpNode.reg(if(rex) Reg.ZMM14 else Reg.ZMM3)
		Op.VM32X -> TODO()
		Op.VM64X -> TODO()
		Op.VM32Y -> TODO()
		Op.VM64Y -> TODO()
		Op.VM32Z -> TODO()
		Op.VM64Z -> TODO()
		Op.K -> OpNode.reg(Reg.K3)
		Op.BND -> OpNode.reg(Reg.BND3)
		Op.T -> TODO()
		Op.MOFFS8 -> TODO()
		Op.MOFFS16 -> TODO()
		Op.MOFFS32 -> TODO()
		Op.MOFFS64 -> TODO()
		Op.SEG -> OpNode.reg(Reg.FS)
		Op.CR -> OpNode.reg(Reg.CR3)
		Op.DR -> OpNode.reg(Reg.DR3)
		Op.FS -> OpNode.reg(Reg.FS)
		Op.GS -> OpNode.reg(Reg.GS)
	}



	private fun test() {
		val nasmBuilder = StringBuilder()
		val context = CompilerContext(emptyList())
		val assembler = Assembler(context)
		nasmBuilder.appendLine("BITS 64")

		for(e in nasmEncs) {
			when(e.mnemonic) {
				"PMULUDQ", "PSUBQ", "PSHUFW", "PSHUFD", "PSHUFHW", "PSHUFLW", "PALIGNR" -> continue
			}
			if(e.ops.any { it.type == OpType.MOFFS }) continue
			if(e.mnemonic in nasmToManualIgnoredMnemonics) continue
			if(e.isAvx) continue

			val mnemonic = Mnemonic.entries.first { it.name == e.mnemonic }

			val op1 = e.ops.getOrNull(0).toOpNode(false)
			val op2 = e.ops.getOrNull(1).toOpNode(false)
			val op3 = e.ops.getOrNull(2).toOpNode(false)
			val op4 = e.ops.getOrNull(3).toOpNode(false)

			val node = InsNode(
				null,
				mnemonic,
				e.ops.size,
				op1,
				op2,
				op3,
				op4
			)

			//assembler.assembleForTesting(node, false)

			nasmBuilder.appendLine(node.printString.replace("xword", "oword"))
		}

		//Files.write(Paths.get("test.eyre.obj"), context.textWriter.getTrimmedBytes())
		Files.writeString(Paths.get("test.asm"), nasmBuilder.toString())
		//Util.run("nasm", "-fwin64", "test.asm")
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
	AVX Instruction generation
	 */



	fun genAvxEncMap() {
		val encs = encsOfType(Mnemonic.Type.AVX)
		val map = HashMap<String, ArrayList<AvxEnc>>()

		fun Op?.toAvxOp() = when(this) {
			null    -> AvxOp.NONE
			Op.X    -> AvxOp.X
			Op.Y    -> AvxOp.Y
			Op.Z    -> AvxOp.Z
			Op.R8   -> AvxOp.R8
			Op.R16  -> AvxOp.R16
			Op.R32  -> AvxOp.R32
			Op.R64  -> AvxOp.R64
			Op.I8   -> AvxOp.I8
			Op.K    -> AvxOp.K
			Op.T    -> AvxOp.T
			Op.MEM  -> AvxOp.MEM
			Op.M8   -> AvxOp.M8
			Op.M16  -> AvxOp.M16
			Op.M32  -> AvxOp.M32
			Op.M64  -> AvxOp.M64
			Op.M128 -> AvxOp.M128
			Op.M256 -> AvxOp.M256
			Op.M512 -> AvxOp.M512
			else    -> error("Invalid AVX op")
		}

		for(e in encs) {
			
		}
	}



	/*
	MMX/SSE instruction generation
	 */



	/**
	 * Generates a mapping of mnemonics to lists of non-human-readable [SseEnc] values for use in code.
	 */
	fun genSseEncMap() {
		val encs = encsOfType(Mnemonic.Type.SSE)
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
	 * Generates an expanded human-readable list of all MMX/SSE instructions.
	 */
	fun genSseEncList() {
		val encs = encsOfType(Mnemonic.Type.SSE)

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