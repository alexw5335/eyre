package eyre.gen

import eyre.*
import eyre.util.NativeReader
import eyre.util.Unique
import eyre.util.Util
import eyre.util.hex8
import java.nio.file.Files
import java.nio.file.Paths

object EncGen {


	private val nasmParser = NasmParser(Files.readAllLines(Paths.get("nasm.txt"))).also { it.read() }
	private val encs = nasmParser.expandedEncs
	private val mnemonicMap = Mnemonic.entries.associateBy { it.name }
	private val gpEncs = encs.filter { it.mnemonic.isGp }
	private val sseEncs = encs.filter { it.mnemonic.isSse }
	private val avxEncs = encs.filter { it.mnemonic.isAvx }



	// AVX/AVX512/MMX/SSE: any IMM ops are always I8 and always the last operand. None have multiple IMM ops.

	fun run() {

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
		Op.I8    -> OpNode.imm(Width.BYTE, IntNode(10))
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



	private fun testEncs() {
		val nasmBuilder = StringBuilder()
		nasmBuilder.appendLine("BITS 64")
		val context = CompilerContext(emptyList())
		val assembler = Assembler(context)
		val tests = ArrayList<EncTest>()
		var error = false

		val testing = listOf(Op.X, Op.X)

		for(e in encs) {
			if(e.ops.any { it.type == OpType.MOFFS || it.type == OpType.VM || it.type == OpType.REL }) continue
			if(e.mnemonic in ignoredTestingMnemonics) continue
			if(e.avx) continue
			if(e.ops != testing) continue

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



	fun genSimdMap(): Map<Mnemonic, SimdGroup> {
		val map = HashMap<Mnemonic, SimdGroup>()
		fun Op.toSimdOp() = SimdOp.entries.firstOrNull { it.op == this } ?: error("Invalid SIMD op: $this")

		for(e in encs) {
			if(e.mnemonic.type != Mnemonic.Type.SSE && e.mnemonic.type != Mnemonic.Type.AVX) continue
			Unique.print(e.opsString)
			if(e.ops.isEmpty()) continue
			if(e.opcode and 0xFF00 != 0) error("Invalid opcode: $e")
			if(e.ops.any { it.type == OpType.VM }) continue

			val simdEnc = SimdEnc(
				e.mnemonic,
				e.opcode,
				e.prefix,
				e.escape,
				e.ext.coerceAtLeast(0),
				e.hasExt,
				e.ops.map { it.toSimdOp() },
				e.o16,
				e.rw,
				e.vexl.value,
				e.vexw.value,
				e.tuple,
				SimdOpEnc.entries.firstOrNull { e.opEnc in it.encs} ?: error("Invalid SIMD op enc: ${e.opEnc}"),
				e.sae,
				e.er,
				e.bcst,
				e.vsib,
				e.k,
				e.z,
				e.avx && !e.evex,
				e.evex
			)

			map.getOrPut(e.mnemonic) { SimdGroup(e.mnemonic) }.add(simdEnc)
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

		for(e in sseEncs) {
			fun Op?.toSseOp() = when(this) {
				null    -> SseOp.NONE
				Op.NONE -> SseOp.NONE
				Op.I8   -> SseOp.I8
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
				else    -> error("Invalid SSE operand: $e")
			}
			val sseEnc = SseEnc(
				e.opcode,
				e.prefix.ordinal,
				e.escape.ordinal,
				e.ext.coerceAtLeast(0),
				e.ops.getOrNull(0).toSseOp(),
				e.ops.getOrNull(1).toSseOp(),
				e.ops.getOrNull(2).toSseOp(),
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
		// NASM inserts REX.W for some reason?
		Mnemonic.ENQCMD,
		Mnemonic.ENQCMDS,
		Mnemonic.INVPCID,
		// Confusing
		Mnemonic.LAR,
		Mnemonic.LSL,
		// Missing F3 prefix
		Mnemonic.PTWRITE,
		// Missing O16
		Mnemonic.RETW,
		// NASM gives two versions, one without REX.W
		Mnemonic.SLDT,
		// ?
		Mnemonic.XABORT
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