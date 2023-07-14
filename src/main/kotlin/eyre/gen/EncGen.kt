package eyre.gen

import eyre.*
import eyre.util.associateFlatMap
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.random.Random

object EncGen {


	private val nasmParser = NasmParser(Files.readAllLines(Paths.get("nasm.txt"))).also { it.read() }
	private val encs = nasmParser.encs
	private val mnemonicMap = Mnemonic.entries.associateBy { it.name }
	private val gpEncs = encs.filter { it.mnemonic.isGp }
	private val sseEncs = encs.filter { it.mnemonic.isSse }
	private val avxEncs = encs.filter { it.mnemonic.isAvx }



	fun run() {
		test()
	}



	private fun test() {
		val map = sseEncs.associateFlatMap { it.mnemonic }
		val lists = ArrayList<ArrayList<Mnemonic>>()
		for(i in 0 ..< 32) lists.add(ArrayList())
		for((mnemonic, encs) in map)
			lists[encs.size].add(mnemonic)
		for((i, list) in lists.withIndex())
			if(list.isNotEmpty())
				println("$i: $list")
	}



	/*
	Testing
	 */



	/*private fun Op?.toOpNode(rex: Boolean): OpNode? = when(this) {
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
			if(e.avx) continue

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
	}*/



	/*
	Mnemonic generation
	 */



	private fun genMnemonics() {
		val mnemonics = HashMap<String, Mnemonic.Type>()

		for(e in encs) {
			when {
				e.avx ->
					mnemonics[e.mnemonic.name] = Mnemonic.Type.AVX
				Op.X in e.ops || Op.MM in e.ops ->
					if(mnemonics[e.mnemonic.name] != Mnemonic.Type.AVX)
						mnemonics[e.mnemonic.name] = Mnemonic.Type.SSE
				else ->
					if(mnemonics[e.mnemonic.name] == null)
						mnemonics[e.mnemonic.name] = Mnemonic.Type.GP
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



	fun genAvxEncMap(): Map<Mnemonic, List<AvxEnc>> {
		val map = HashMap<Mnemonic, ArrayList<AvxEnc>>()

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
			Op.VM32X, Op.VM32Y, Op.VM32Z -> AvxOp.M32
			Op.VM64X, Op.VM64Y, Op.VM64Z -> AvxOp.M64
			else    -> error("Invalid AVX op: $this")
		}

		fun OpEnc.toAvxOpEnc(): AvxOpEnc = when(this) {
			OpEnc.NONE -> AvxOpEnc.NONE
			OpEnc.M -> AvxOpEnc.M
			OpEnc.R -> AvxOpEnc.R
			OpEnc.RMI,
			OpEnc.RM -> AvxOpEnc.RM
			OpEnc.MRI,
			OpEnc.MR -> AvxOpEnc.MR
			OpEnc.VMI,
			OpEnc.VM -> AvxOpEnc.VM
			OpEnc.RVMI,
			OpEnc.RVM -> AvxOpEnc.RVM
			OpEnc.MVR -> AvxOpEnc.MVR
			OpEnc.RMVI,
			OpEnc.RMV -> AvxOpEnc.RMV
			OpEnc.RVMS -> AvxOpEnc.RVMS
			else -> error("Invalid avx op enc: $this")
		}

		for(e in avxEncs) {
			val avxEnc = AvxEnc(
				e.opcode,
				e.prefix,
				e.escape,
				e.ext,
				e.hasExt,
				e.ops.map { it.toAvxOp() },
				e.ops.getOrNull(0).toAvxOp(),
				e.ops.getOrNull(1).toAvxOp(),
				e.ops.getOrNull(2).toAvxOp(),
				e.ops.getOrNull(3).toAvxOp(),
				e.vexl.value,
				e.vexw.value,
				e.tuple,
				e.opEnc.toAvxOpEnc(),
				e.sae,
				e.er,
				e.bcst,
				e.vsib,
				e.k,
				e.z,
				e.evex
			)

			map.getOrPut(e.mnemonic, ::ArrayList) += avxEnc
		}

		val toAdd = ArrayList<AvxEnc>()

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

		return map
	}



	/*
	MMX/SSE instruction generation
	 */



	/**
	 * Generates a mapping of mnemonics to lists of non-human-readable [SseEnc] values for use in code.
	 */
	fun genSseEncMap() {
		val map = HashMap<Mnemonic, ArrayList<SseEnc>>()

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

		for(e in sseEncs) {
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
				e.rw,
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
		"POPW",
		"PUSHW",
		"LEAVEW",
		"ENTER",
		"WAIT",
		"JMPF",
		"CALLF",
		"ENTERW",
		"SYSEXITQ",
		"SYSRETQ"
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
	 * - NASM extensions that contain any AVX or AVX-512 operands.
	 * - Does not contain all VEX encodings. Some are contained within extensions that also include AVX-512 encodings
	 * - None of these extensions overlap. All of them should be the sole extension of their encodings
	 * - GFNI and VAES are not included as they overlap with AVX-512
	 */
	private val avxExtensions = setOf(
		NasmExt.AVX,
		NasmExt.AVX2,
		NasmExt.AVXIFMA,
		NasmExt.AVXNECONVERT,
		NasmExt.AVXVNNIINT8,
		NasmExt.BMI1,
		NasmExt.BMI2,
		NasmExt.CMPCCXADD,
		NasmExt.FMA
	)


}