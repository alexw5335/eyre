package eyre.gen

import eyre.*
import eyre.util.NativeReader
import eyre.util.Util
import eyre.util.hex8
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.random.Random

@Suppress("unused")
object EncGen {


	private val nasmParser = NasmParser(Files.readAllLines(Paths.get("nasm.txt"))).also { it.read() }
	private val encs = nasmParser.expandedEncs
	private val gpEncs = encs.filter { it.mnemonic.isGp }
	private val sseEncs = encs.filter { it.mnemonic.isSse }
	private val avxEncs = encs.filter { it.mnemonic.isAvx }



	val map = HashMap<Mnemonic, ArrayList<NasmEnc>>().apply {
		for(enc in encs)
			getOrPut(enc.mnemonic, ::ArrayList).add(enc)
	}



	fun run() {
		for((m, encs) in nasmParser.encsMap) {
			var hasM = false
			var hasVM = false
			for(e in encs) {
				if(e.ops.any { it.type == OpType.VM }) hasVM = true
				if(e.ops.any { it.type == OpType.M }) hasM = true
			}
			if(hasVM) println(m)
		}
		//testEncs(false)
	}



	/*
	Testing
	 */



	private fun randomMem(width: Width?): OpNode {
		val index = BinaryNode(
			BinaryOp.MUL,
			RegNode(Reg.r64().let { if(it.isInvalidIndex) Reg.RAX else it }),
			IntNode(1L shl Random.nextInt(3))
		)
		val base = RegNode(Reg.r64().let { if(it.value == 5) Reg.RAX else it })
		val disp = IntNode(Random.nextInt(512).toLong())
		fun add(a: AstNode, b: AstNode) = BinaryNode(BinaryOp.ADD, a, b)
		val node = when(Random.nextInt(7)) {
			0 -> base
			1 -> index
			2 -> disp
			3 -> add(base, index)
			4 -> add(base, disp)
			5 -> add(index, disp)
			6 -> add(base, add(index, disp))
			else -> error("?")
		}
		return OpNode.mem(width, node)
	}



	private fun Op?.toRandomNode(): OpNode? = when(this) {
		null     -> null
		Op.NONE  -> null
		Op.R8    -> OpNode.reg(Reg.r8(Random.nextInt(16).let { if(it in 4..7) it + 4 else it }))
		Op.R16   -> OpNode.reg(Reg.r16(Random.nextInt(16)))
		Op.R32   -> OpNode.reg(Reg.r32(Random.nextInt(16)))
		Op.R64   -> OpNode.reg(Reg.r64(Random.nextInt(16)))
		Op.MEM   -> randomMem(null)
		Op.M8    -> randomMem(Width.BYTE)
		Op.M16   -> randomMem(Width.WORD)
		Op.M32   -> randomMem(Width.DWORD)
		Op.M64   -> randomMem(Width.QWORD)
		Op.M80   -> randomMem(Width.TWORD)
		Op.M128  -> randomMem(Width.XWORD)
		Op.M256  -> randomMem(Width.YWORD)
		Op.M512  -> randomMem(Width.ZWORD)
		Op.I8    -> OpNode.imm(Width.BYTE, IntNode(Random.nextLong(0xF)))
		Op.I16   -> OpNode.imm(null, IntNode(Random.nextLong(10))) // NASM doesn't use PUSH I16 for some reason
		Op.I32   -> OpNode.imm(null, IntNode(Random.nextLong(0xFFFFFF)))
		Op.I64   -> OpNode.imm(null, IntNode(Random.nextLong(0xFFFFFFFFFF)))
		Op.AL    -> OpNode.reg(Reg.AL)
		Op.AX    -> OpNode.reg(Reg.AX)
		Op.EAX   -> OpNode.reg(Reg.EAX)
		Op.RAX   -> OpNode.reg(Reg.RAX)
		Op.CL    -> OpNode.reg(Reg.CL)
		Op.ECX   -> OpNode.reg(Reg.ECX)
		Op.RCX   -> OpNode.reg(Reg.RCX)
		Op.DX    -> OpNode.reg(Reg.DX)
		Op.REL8  -> OpNode.imm(null, IntNode(Random.nextLong(0xF)))
		Op.REL16 -> OpNode.imm(null, IntNode(Random.nextLong(0xFFF)))
		Op.REL32 -> OpNode.imm(null, IntNode(Random.nextLong(0xFFFFFF)))
		Op.ST    -> OpNode.reg(Reg.st(Random.nextInt(8)))
		Op.ST0   -> OpNode.reg(Reg.ST0)
		Op.ONE   -> OpNode.imm(null, IntNode(1))
		Op.MM    -> OpNode.reg(Reg.mm(Random.nextInt(8)))
		Op.X     -> OpNode.reg(Reg.x(Random.nextInt(16)))
		Op.Y     -> OpNode.reg(Reg.y(Random.nextInt(16)))
		Op.Z     -> OpNode.reg(Reg.z(Random.nextInt(16)))
		Op.K     -> OpNode.reg(Reg.k(Random.nextInt(8)))
		Op.BND   -> OpNode.reg(Reg.bnd(Random.nextInt(4)))
		Op.T     -> OpNode.reg(Reg.tmm(Random.nextInt(8)))
		Op.SEG   -> OpNode.reg(Reg.seg(Random.nextInt(6)))
		Op.CR    -> OpNode.reg(Reg.cr(Random.nextInt(9)))
		Op.DR    -> OpNode.reg(Reg.dr(Random.nextInt(8)))
		Op.FS    -> OpNode.reg(Reg.FS)
		Op.GS    -> OpNode.reg(Reg.GS)
		Op.VM32X -> OpNode.mem(Width.DWORD, RegNode(Reg.x(Random.nextInt(16))))
		Op.VM64X -> OpNode.mem(Width.QWORD, RegNode(Reg.x(Random.nextInt(16))))
		Op.VM32Y -> OpNode.mem(Width.DWORD, RegNode(Reg.y(Random.nextInt(16))))
		Op.VM64Y -> OpNode.mem(Width.QWORD, RegNode(Reg.y(Random.nextInt(16))))
		Op.VM32Z -> OpNode.mem(Width.DWORD, RegNode(Reg.z(Random.nextInt(16))))
		Op.VM64Z -> OpNode.mem(Width.QWORD, RegNode(Reg.z(Random.nextInt(16))))
		else     -> error("Invalid op: $this")
	}



	data class EncTest(val node: InsNode, val enc: NasmEnc?, val pos: Int, val length: Int) {
		override fun toString() = "${node.printString}    $enc    ($pos, $length)"
	}



	private fun testEncs(allowErrors: Boolean) {
		val nasmBuilder = StringBuilder()
		nasmBuilder.appendLine("BITS 64")
		val context = CompilerContext(emptyList())
		val assembler = Assembler(context)
		val tests = ArrayList<EncTest>()
		var error = false

		for(e in encs) {
			if(e.ops.any { it.type == OpType.MOFFS || it.type == OpType.REL }) continue
			if(e.mnemonic in ignoredTestingMnemonics) continue
			if(e.evex) continue

			val op1 = e.ops.getOrNull(0).toRandomNode()
			val op2 = e.ops.getOrNull(1).toRandomNode()
			val op3 = e.ops.getOrNull(2).toRandomNode()
			val op4 = e.ops.getOrNull(3).toRandomNode()
			val high = (op1?.high ?: 0) or (op2?.high ?: 0) or (op3?.high ?: 0) or (op4?.high ?: 0)

			val node = InsNode(
				null,
				e.mnemonic,
				e.ops.size,
				high,
				op1,
				op2,
				op3,
				op4
			)

			nasmBuilder.appendLine(node.nasmString)
			try {
				val (start, length) = assembler.assembleForTesting(node)
				val nasmEnc = if(e.mnemonic.isAvx || e.mnemonic.isSse) assembler.getEnc(node)!! else null
				tests += EncTest(node, nasmEnc, start, length)
			} catch(e: Exception) {
				e.printStackTrace()
				error = true
				if(!allowErrors) break
			}
		}

		if(error) {
			System.err.println("Assembler encountered one or more errors")
			return
		}

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
					println("ERROR: ${test.node.printString}    ${test.enc}")
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
				e.ops.isEmpty() ->
					mnemonics[e.mnemonic.name] = Mnemonic.Type.GP
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
		//Mnemonic.TEST,
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
		Mnemonic.XABORT,
		// ?
		Mnemonic.TILELOADD,
		Mnemonic.TILELOADDT1,
		Mnemonic.TILESTORED,
		// ? Not sure what's wrong, RRMR seems to be assembled as RRRR by NASM?
		Mnemonic.VBLENDVPD,
		Mnemonic.VBLENDVPS,
		Mnemonic.VPBLENDVB,
		// Multiple valid encodings
		Mnemonic.PEXTRW,
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