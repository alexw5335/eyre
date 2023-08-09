package eyre.gen

import eyre.*
import eyre.util.*
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
		//genMnemonics()
		//genZeroOpOpcodes()
		testEncs(false)
	}




	/*
	Testing
	 */



	private fun randomMem(width: Width?): OpNode {
		val index = BinaryNode(
			BinaryOp.MUL,
			RegNode(Reg.r64(Random.nextInt(16)).let { if(it.isInvalidIndex) Reg.RAX else it }),
			IntNode(1L shl Random.nextInt(3))
		)
		val base = RegNode(Reg.r64(Random.nextInt(16)).let { if(it.value == 5) Reg.RAX else it })
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



	private fun NasmOp.random(): OpNode = when(this) {
		NasmOp.NONE  -> OpNode.NULL
		NasmOp.R8    -> OpNode.reg(Reg.r8(Random.nextInt(16).let { if(it in 4..7) it + 4 else it }))
		NasmOp.R16   -> OpNode.reg(Reg.r16(Random.nextInt(16)))
		NasmOp.R32   -> OpNode.reg(Reg.r32(Random.nextInt(16)))
		NasmOp.R64   -> OpNode.reg(Reg.r64(Random.nextInt(16)))
		NasmOp.MEM   -> randomMem(null)
		NasmOp.M8    -> randomMem(Width.BYTE)
		NasmOp.M16   -> randomMem(Width.WORD)
		NasmOp.M32   -> randomMem(Width.DWORD)
		NasmOp.M64   -> randomMem(Width.QWORD)
		NasmOp.M80   -> randomMem(Width.TWORD)
		NasmOp.M128  -> randomMem(Width.XWORD)
		NasmOp.M256  -> randomMem(Width.YWORD)
		NasmOp.M512  -> randomMem(Width.ZWORD)
		NasmOp.I8    -> OpNode.imm(Width.BYTE, IntNode(Random.nextLong(0xF)))
		NasmOp.I16   -> OpNode.imm(null, IntNode(Random.nextLong(10))) // NASM doesn't use PUSH I16 for some reason
		NasmOp.I32   -> OpNode.imm(null, IntNode(Random.nextLong(0xFFFFFF)))
		NasmOp.I64   -> OpNode.imm(null, IntNode(Random.nextLong(0xFFFFFFFFFF)))
		NasmOp.AL    -> OpNode.reg(Reg.AL)
		NasmOp.AX    -> OpNode.reg(Reg.AX)
		NasmOp.EAX   -> OpNode.reg(Reg.EAX)
		NasmOp.RAX   -> OpNode.reg(Reg.RAX)
		NasmOp.CL    -> OpNode.reg(Reg.CL)
		NasmOp.ECX   -> OpNode.reg(Reg.ECX)
		NasmOp.RCX   -> OpNode.reg(Reg.RCX)
		NasmOp.DX    -> OpNode.reg(Reg.DX)
		NasmOp.REL8  -> OpNode.imm(null, IntNode(Random.nextLong(0xF)))
		NasmOp.REL16 -> OpNode.imm(null, IntNode(Random.nextLong(0xFFF)))
		NasmOp.REL32 -> OpNode.imm(null, IntNode(Random.nextLong(0xFFFFFF)))
		NasmOp.ST    -> OpNode.reg(Reg.st(Random.nextInt(8)))
		NasmOp.ST0   -> OpNode.reg(Reg.ST0)
		NasmOp.ONE   -> OpNode.imm(null, IntNode(1))
		NasmOp.MM    -> OpNode.reg(Reg.mm(Random.nextInt(8)))
		NasmOp.X     -> OpNode.reg(Reg.x(Random.nextInt(16)))
		NasmOp.Y     -> OpNode.reg(Reg.y(Random.nextInt(16)))
		NasmOp.Z     -> OpNode.reg(Reg.z(Random.nextInt(16)))
		NasmOp.K     -> OpNode.reg(Reg.k(Random.nextInt(8)))
		NasmOp.BND   -> OpNode.reg(Reg.bnd(Random.nextInt(4)))
		NasmOp.T     -> OpNode.reg(Reg.tmm(Random.nextInt(8)))
		NasmOp.SEG   -> OpNode.reg(Reg.seg(Random.nextInt(6)))
		NasmOp.CR    -> OpNode.reg(Reg.cr(Random.nextInt(9)))
		NasmOp.DR    -> OpNode.reg(Reg.dr(Random.nextInt(8)))
		NasmOp.FS    -> OpNode.reg(Reg.FS)
		NasmOp.GS    -> OpNode.reg(Reg.GS)
		NasmOp.VM32X -> OpNode.mem(Width.DWORD, RegNode(Reg.x(Random.nextInt(16))))
		NasmOp.VM64X -> OpNode.mem(Width.QWORD, RegNode(Reg.x(Random.nextInt(16))))
		NasmOp.VM32Y -> OpNode.mem(Width.DWORD, RegNode(Reg.y(Random.nextInt(16))))
		NasmOp.VM64Y -> OpNode.mem(Width.QWORD, RegNode(Reg.y(Random.nextInt(16))))
		NasmOp.VM32Z -> OpNode.mem(Width.DWORD, RegNode(Reg.z(Random.nextInt(16))))
		NasmOp.VM64Z -> OpNode.mem(Width.QWORD, RegNode(Reg.z(Random.nextInt(16))))
		else     -> error("Invalid op: $this")
	}



	data class EncTest(val node: InsNode, val enc: NasmEnc?, val pos: Int, val length: Int) {
		override fun toString() = "${node.printString}    ${enc?.printString}    ($pos, $length)"
	}



	private fun testEncs(allowErrors: Boolean) {
		val nasmBuilder = StringBuilder()
		nasmBuilder.appendLine("BITS 64")
		val context = CompilerContext(emptyList())
		val assembler = Assembler(context)
		val tests = ArrayList<EncTest>()
		var error = false

		val invalidOps = listOf(NasmOpType.MOFFS, NasmOpType.REL, NasmOpType.SEG)

		for(e in encs) {
			if(e.ops.any { it.type in invalidOps || it == NasmOp.I64 }) continue
			if(e.mnemonic in ignoredTestingMnemonics) continue
			if(e.evex) continue

			val node = when(e.ops.size) {
				0    -> InsNode(e.mnemonic)
				1    -> InsNode(e.mnemonic, e.op1.random())
				2    -> InsNode(e.mnemonic, e.op1.random(), e.op2.random())
				3    -> InsNode(e.mnemonic, e.op1.random(), e.op2.random(), e.op3.random())
				4    -> InsNode(e.mnemonic, e.op1.random(), e.op2.random(), e.op3.random(), e.op4.random())
				else -> error("")
			}

			nasmBuilder.appendLine(node.nasmString)
			try {
				val (start, length) = assembler.assembleDebug(node)
				val nasmEnc = if(e.mnemonic.isAvx || e.mnemonic.isSse) assembler.getEnc(node) else null
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
					println("ERROR: ${test.node.printString}    ${test.enc?.printString}")
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
				NasmOp.ST in e.ops || NasmOp.ST0 in e.ops ->
					mnemonics[e.mnemonic.name] = Mnemonic.Type.FPU
				e.ops.isEmpty() ->
					if(mnemonics[e.mnemonic.name] == null)
						mnemonics[e.mnemonic.name] = Mnemonic.Type.GP
				e.avx ->
					mnemonics[e.mnemonic.name] = Mnemonic.Type.AVX
				NasmOp.X in e.ops || NasmOp.MM in e.ops ->
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



	private fun genZeroOpOpcodes() {
		print("val ZERO_OP_OPCODES = intArrayOf(\n\t")
		var length = 0
		for(m in Mnemonic.entries) {
			val opcode = EncGenLists.zeroOpOpcodes[m] ?: 0
			val opcodeString = opcode.toString()
			print(opcodeString)
			print(", ")
			length += opcodeString.length + 2
			if(m == Mnemonic.entries.last())
				println()
			else if(length > 40) {
				print("\n\t")
				length = 0
			}
		}
		println(")")
	}



	/*
	Collections
	 */



	private val ignoredTestingMnemonics = setOf(
		// MPX
		Mnemonic.BNDLDX,
		Mnemonic.BNDMOV,
		Mnemonic.BNDCU,
		Mnemonic.BNDCL,
		Mnemonic.BNDSTX,
		Mnemonic.BNDMOV,
		Mnemonic.BNDCN,
		Mnemonic.BNDMK,
		// NASM width errors
		Mnemonic.PMULUDQ,
		Mnemonic.PSUBQ,
		Mnemonic.PSHUFW,
		Mnemonic.PSHUFD,
		Mnemonic.PSHUFHW,
		Mnemonic.PSHUFLW,
		Mnemonic.PALIGNR,
		// NASM inserts REX.W for some reason?
		Mnemonic.ENQCMD,
		Mnemonic.ENQCMDS,
		Mnemonic.INVPCID,
		// Missing F3 prefix
		Mnemonic.PTWRITE,
		// Missing O16
		Mnemonic.RETW,
		// NASM gives two versions, one without REX.W
		Mnemonic.SLDT,
		// ?
		Mnemonic.TILELOADD,
		Mnemonic.TILELOADDT1,
		Mnemonic.TILESTORED,
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