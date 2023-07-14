package eyre.gen

import eyre.*
import eyre.util.NativeReader
import eyre.util.Util
import eyre.util.hex8
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.min

object EncGen {


	private val nasmParser = NasmParser(Files.readAllLines(Paths.get("nasm.txt"))).also { it.read() }
	private val encs = nasmParser.encs
	private val mnemonicMap = Mnemonic.entries.associateBy { it.name }
	private val gpEncs = encs.filter { it.mnemonic.isGp }
	private val sseEncs = encs.filter { it.mnemonic.isSse }
	private val avxEncs = encs.filter { it.mnemonic.isAvx }



	fun run() {
		testNasmEncs()
	}



	/*
	Testing
	 */



	private fun Op?.toNode(): OpNode? = when(this) {
		null     -> null
		Op.NONE  -> null
		Op.R8    -> OpNode.reg(Reg.DL)
		Op.R16   -> OpNode.reg(Reg.DX)
		Op.R32   -> OpNode.reg(Reg.EDX)
		Op.R64   -> OpNode.reg(Reg.RDX)
		Op.MEM   -> OpNode.mem(null, RegNode(Reg.RAX))
		Op.M8    -> OpNode.mem(Width.BYTE, RegNode(Reg.RDX))
		Op.M16   -> OpNode.mem(Width.WORD, RegNode(Reg.RDX))
		Op.M32   -> OpNode.mem(Width.DWORD, RegNode(Reg.RDX))
		Op.M64   -> OpNode.mem(Width.QWORD, RegNode(Reg.RDX))
		Op.M80   -> OpNode.mem(Width.TWORD, RegNode(Reg.RDX))
		Op.M128  -> OpNode.mem(Width.XWORD, RegNode(Reg.RDX))
		Op.M256  -> OpNode.mem(Width.YWORD, RegNode(Reg.RDX))
		Op.M512  -> OpNode.mem(Width.ZWORD, RegNode(Reg.RDX))
		Op.I8    -> OpNode.imm(null, IntNode(10))
		Op.I16   -> OpNode.imm(null, IntNode(10))
		Op.I32   -> OpNode.imm(null, IntNode(10))
		Op.I64   -> OpNode.imm(null, IntNode(10))
		Op.AL    -> OpNode.reg(Reg.AL)
		Op.AX    -> OpNode.reg(Reg.AX)
		Op.EAX   -> OpNode.reg(Reg.EAX)
		Op.RAX   -> OpNode.reg(Reg.RAX)
		Op.CL    -> OpNode.reg(Reg.CL)
		Op.ECX   -> OpNode.reg(Reg.ECX)
		Op.RCX   -> OpNode.reg(Reg.RCX)
		Op.DX    -> OpNode.reg(Reg.DX)
		Op.REL8  -> OpNode.imm(null, IntNode(10))
		Op.REL16 -> OpNode.imm(null, IntNode(10))
		Op.REL32 -> OpNode.imm(null, IntNode(10))
		Op.ST    -> OpNode.reg(Reg.ST3)
		Op.ST0   -> OpNode.reg(Reg.ST0)
		Op.ONE   -> OpNode.imm(null, IntNode(1))
		Op.MM    -> OpNode.reg(Reg.MM3)
		Op.X     -> OpNode.reg(Reg.XMM3)
		Op.Y     -> OpNode.reg(Reg.YMM3)
		Op.Z     -> OpNode.reg(Reg.ZMM3)
		Op.K     -> OpNode.reg(Reg.K3)
		Op.BND   -> OpNode.reg(Reg.BND3)
		Op.T     -> OpNode.reg(Reg.TMM3)
		Op.SEG   -> OpNode.reg(Reg.FS)
		Op.CR    -> OpNode.reg(Reg.CR3)
		Op.DR    -> OpNode.reg(Reg.DR3)
		Op.FS    -> OpNode.reg(Reg.FS)
		Op.GS    -> OpNode.reg(Reg.GS)
		Op.VM32X -> TODO()
		Op.VM64X -> TODO()
		Op.VM32Y -> TODO()
		Op.VM64Y -> TODO()
		Op.VM32Z -> TODO()
		Op.VM64Z -> TODO()
		else     -> error("Invalid op: $this")
	}



	class EncTest(val node: InsNode, val pos: Int, val length: Int) {
		override fun toString() = "node=${node.printString}, pos=$pos, length=$length"
	}



	private fun testNasmEncs() {
		val nasmBuilder = StringBuilder()
		nasmBuilder.appendLine("BITS 64")
		val context = CompilerContext(emptyList())
		val assembler = Assembler(context)
		val tests = ArrayList<EncTest>()
		var error = false

		for(e in nasmParser.expandedEncs.filter { it.mnemonic.type == Mnemonic.Type.GP }) {
			if(e.ops.any { it.type == OpType.MOFFS || it.type == OpType.VM }) continue
			if(e.mnemonic in ignoredTestingMnemonics) continue
			if(e.evex) continue

			val op1 = e.ops.getOrNull(0).toNode()
			val op2 = e.ops.getOrNull(1).toNode()
			val op3 = e.ops.getOrNull(2).toNode()
			val op4 = e.ops.getOrNull(3).toNode()

			val node = InsNode(
				null,
				e.mnemonic,
				e.ops.size,
				op1,
				op2,
				op3,
				op4
			)

			nasmBuilder.appendLine(node.nasmString)
			try {
				val (start, length) = assembler.assembleForTesting(node, false)
				tests += EncTest(node, start, length)
			} catch(e: Exception) {
				e.printStackTrace()
				error = true
			}
		}

		if(error) error("Assembler encountered one or more errors")

		val nasmInputPath = Paths.get("test.asm")
		Files.writeString(nasmInputPath, nasmBuilder.toString())
		Util.run("nasm", "-fwin64", "test.asm")
		val nasmOutputPath = Paths.get("test.obj")
		val reader = NativeReader(Files.readAllBytes(nasmOutputPath))
		reader.pos = 20
		if(reader.ascii(5) != ".text") error("NASM error")
		val nasmBytes = reader.bytes(reader.i32(40), reader.i32(36))
		val eyreBytes = context.textWriter.getTrimmedBytes()

		for(test in tests) {
			println(test)
			if(eyreBytes.size < test.pos + test.length) error("?")
			for(i in 0 ..< test.length) {
				if(nasmBytes[test.pos + i] != eyreBytes[test.pos + i]) {
					println("ERROR: ${test.node.printString}")
					for(j in 0 ..< test.length)
						println("$j ${nasmBytes[test.pos + j].hex8} ${eyreBytes[test.pos + j].hex8}")
					return
				}
			}
		}
	}



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



	private val ignoredTestingMnemonics = setOf(
		// NASM width errors
		Mnemonic.PMULUDQ,
		Mnemonic.PSUBQ,
		Mnemonic.PSHUFW,
		Mnemonic.PSHUFD,
		Mnemonic.PSHUFHW,
		Mnemonic.PSHUFLW,
		Mnemonic.PALIGNR,
		// Custom mnemonics
		Mnemonic.POPW,
		Mnemonic.PUSHW,
		Mnemonic.LEAVEW,
		Mnemonic.ENTER,
		Mnemonic.WAIT,
		Mnemonic.JMPF,
		Mnemonic.CALLF,
		Mnemonic.ENTERW,
		Mnemonic.SYSEXITQ,
		Mnemonic.SYSRETQ,
		// TEMP
		Mnemonic.BNDCL,
		Mnemonic.BNDCN,
		Mnemonic.BNDCU,
		Mnemonic.BNDLDX,
		Mnemonic.BNDMK,
		Mnemonic.BNDMOV,
		Mnemonic.BNDSTX,
		// TEMP: SREG
		Mnemonic.MOV,
		// TEMP: NASM allows MEM as second operand
		Mnemonic.TEST,
		// Explicit operands
		Mnemonic.LOOP,
		Mnemonic.LOOPE,
		Mnemonic.LOOPNE,
		Mnemonic.LOOPNZ,
		Mnemonic.LOOPZ,
		Mnemonic.HRESET,
		// NASM gives M64, Intel gives M8
		Mnemonic.PREFETCHW,
	)



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