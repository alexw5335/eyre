package eyre.gen

import eyre.*
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.random.Random
import kotlin.system.exitProcess

object EncTesting {


	data class EncTest(
		val node: InsNode,
		val enc: ParsedEnc,
		val pos: Int,
		val size: Int
	)



	/*
	Operand generation
	 */



	private fun randomMem(width: Width, vm: Reg? = null): OpNode {
		val index = BinNode(
			BinOp.MUL,
			RegNode(vm ?: Reg.r64(Random.nextInt(16)).let { if(it.isInvalidIndex) Reg.RAX else it }),
			IntNode(1L shl Random.nextInt(3))
		)

		val base = RegNode(Reg.r64(Random.nextInt(16)).let { if(it.value == 5) Reg.RAX else it })
		val disp = IntNode(Random.nextInt(512).toLong())
		fun add(a: Node, b: Node) = BinNode(BinOp.ADD, a, b)

		val node: Node = if(vm != null) {
			when(Random.nextInt(4)) {
				0 -> index
				1 -> add(index, base)
				2 -> add(index, disp)
				3 -> add(index, add(base, disp))
				else -> error("")
			}
		} else {
			when(Random.nextInt(6)) {
				0 -> base
				1 -> index
				2 -> add(base, index)
				3 -> add(base, disp)
				4 -> add(index, disp)
				5 -> add(base, add(index, disp))
				else -> error("")
			}
		}

		return OpNode.mem(node, width)
	}



	private fun randomOp(op: Op): OpNode = when(op) {
		Op.NONE  -> OpNode.NONE
		Op.R8    -> OpNode.reg(Reg.r8(Random.nextInt(16).let { if(it in 4..7) it + 4 else it }))
		Op.R16   -> OpNode.reg(Reg.r16(Random.nextInt(16)))
		Op.R32   -> OpNode.reg(Reg.r32(Random.nextInt(16)))
		Op.R64   -> OpNode.reg(Reg.r64(Random.nextInt(16)))
		Op.M8    -> randomMem(op.width)
		Op.M16   -> randomMem(Width.WORD)
		Op.M32   -> randomMem(Width.DWORD)
		Op.M64   -> randomMem(Width.QWORD)
		Op.M80   -> randomMem(Width.TWORD)
		Op.M128  -> randomMem(Width.XWORD)
		Op.M256  -> randomMem(Width.YWORD)
		Op.M512  -> randomMem(Width.ZWORD)
		Op.MEM   -> randomMem(op.width)
		Op.MM    -> OpNode.reg(Reg.mm(Random.nextInt(8)))
		Op.X     -> OpNode.reg(Reg.x(Random.nextInt(16)))
		Op.Y     -> OpNode.reg(Reg.y(Random.nextInt(16)))
		Op.DX    -> OpNode.reg(Reg.DX)
		Op.CL    -> OpNode.reg(Reg.CL)
		Op.AL    -> OpNode.reg(Reg.AL)
		Op.AX    -> OpNode.reg(Reg.AX)
		Op.EAX   -> OpNode.reg(Reg.EAX)
		Op.RAX   -> OpNode.reg(Reg.RAX)
		Op.I8    -> OpNode.imm(IntNode(0x11), Width.NONE)
		Op.I16   -> OpNode.imm(IntNode(0x111), Width.NONE)
		Op.I32   -> OpNode.imm(IntNode(0x11111), Width.NONE)
		Op.I64   -> OpNode.imm(IntNode(0x111111111L), Width.NONE)
		Op.REL8  -> OpNode.imm(IntNode(0x11), Width.NONE)
		Op.REL32 -> OpNode.imm(IntNode(0x11111), Width.NONE)
		Op.FS    -> OpNode.reg(Reg.FS)
		Op.GS    -> OpNode.reg(Reg.GS)
		Op.SEG   -> OpNode.reg(Reg.seg(Random.nextInt(6)))
		Op.CR    -> OpNode.reg(Reg.cr(Random.nextInt(9)))
		Op.DR    -> OpNode.reg(Reg.dr(Random.nextInt(8)))
		Op.ONE   -> OpNode.imm(IntNode(1), Width.NONE)
		Op.ST    -> OpNode.reg(Reg.ST1)
		Op.ST0   -> OpNode.reg(Reg.ST0)
		Op.VM32X -> randomMem(Width.DWORD, Reg.x(Random.nextInt(16)))
		Op.VM64X -> randomMem(Width.QWORD, Reg.x(Random.nextInt(16)))
		Op.VM32Y -> randomMem(Width.DWORD, Reg.y(Random.nextInt(16)))
		Op.VM64Y -> randomMem(Width.QWORD, Reg.y(Random.nextInt(16)))
		Op.VM32Z -> randomMem(Width.DWORD, Reg.z(Random.nextInt(16)))
		Op.VM64Z -> randomMem(Width.QWORD, Reg.z(Random.nextInt(16)))
		Op.A, Op.R, Op.M, Op.I,
		Op.RM, Op.XM, Op.YM, Op.MMM,
		Op.RM8, Op.RM16, Op.RM32, Op.RM64,
		Op.MMM64, Op.XM8, Op.XM16, Op.XM32,
		Op.XM64, Op.XM128, Op.YM8, Op.YM16,
		Op.YM32, Op.YM64, Op.YM128, Op.YM256 ->
			error("Unexpected compound operand: $op")
	}



	private fun Node.exprString(): String = when(this) {
		is IntNode -> value.toString()
		is RegNode -> value.toString()
		is UnNode  -> "${op.string}${node.exprString()}"
		is BinNode -> "${left.exprString()} ${op.string} ${right.exprString()}"
		else       -> error("Invalid node: $this")
	}

	private fun OpNode.nasmString(): String = when(type) {
		OpType.MEM -> "${width.opString.replace('x', 'o')}[${node.exprString()}]"
		OpType.IMM -> "${width.opString.replace('x', 'o')}${node.exprString()}"
		else       -> reg.toString()
	}

	private fun InsNode.nasmString(): String = buildString {
		append(mnemonic)
		if(op1.isNone) return@buildString
		append(" ${op1.nasmString()}")
		if(op2.isNone) return@buildString
		append(", ${op2.nasmString()}")
		if(op3.isNone) return@buildString
		append(", ${op3.nasmString()}")
		if(op4.isNone) return@buildString
		append(", ${op4.nasmString()}")
	}



	/*
	Testing predicates
	 */



	private val ignoredMnemonics = setOf(
		// Pseudo mnemonics
		Mnemonic.DLLCALL, Mnemonic.RETURN,
		// Custom mnemonics
		Mnemonic.CALLF, Mnemonic.JMPF, Mnemonic.SYSRETQ, Mnemonic.PUSHW,
		Mnemonic.POPW, Mnemonic.ENTERW, Mnemonic.LEAVEW, Mnemonic.SYSEXITQ,
		// Future encodings not yet supported by NASM
		Mnemonic.AOR, Mnemonic.AXOR,
		// NASM gives MEM, INTEL gives M8
		Mnemonic.PREFETCHW, Mnemonic.CLDEMOTE, Mnemonic.CLWB, Mnemonic.CLFLUSHOPT, Mnemonic.CLFLUSH,
		// NASM gives MEM, INTEL gives M64
		Mnemonic.VMPTRLD, Mnemonic.VMPTRST, Mnemonic.VMXON, Mnemonic.VMCLEAR,
		// NASM gives MMXRM, but doesn't accept QWORD mem for some reason
		Mnemonic.PMULUDQ, Mnemonic.PSUBQ, Mnemonic.PSHUFW,
		// Nasm gives XMMRM, but doesn't accept XWORD mem for some reason
		Mnemonic.PALIGNR,
		// NASM gives MEM, manual gives M128
		Mnemonic.PSHUFD, Mnemonic.PSHUFLW, Mnemonic.PSHUFHW,
		// NASM gives M128 rather than M64?
		Mnemonic.CMPSD,
		// NASM doesn't insert F3 prefix for some reason
		Mnemonic.PTWRITE,
		// NASM inserts 48 for some reason
		Mnemonic.INVPCID, Mnemonic.ENQCMD, Mnemonic.ENQCMDS,
		// Many duplicate encodings
		Mnemonic.MOVQ
	)



	private fun ParsedEnc.shouldTest(): Boolean = when {
		mnemonic in ignoredMnemonics -> false
		// Nasm doesn't handle I16
		mnemonic == Mnemonic.PUSH && op1 == Op.I16 -> false
		// Nasm doesn't allow R32_RM32
		mnemonic == Mnemonic.MOVSXD && op1 == Op.R32 -> false
		// Nasm handles Jcc oddly ????
		op1 == Op.REL8 || op1 == Op.REL32 -> false
		// Nasm handles MOV with seg very oddly
		Op.SEG in ops -> false
		// Nasm doesn't insert 66
		mnemonic == Mnemonic.RETW && op1 == Op.I16 -> false
		// Nasm doesn't insert 48
		mnemonic == Mnemonic.SLDT && op1 == Op.R64 -> false
		else -> true
	}



	/*
	Testing
	 */



	private fun printDebug(test: EncTest, nasmBytes: ByteArray, eyreBytes: ByteArray) {
		System.err.println("ERROR: ${NodeStrings.string(test.node)} -- ${test.enc}")
		for(i in 0 ..< test.size) {
			val nasmByte = nasmBytes[test.pos + i]
			val eyreByte = eyreBytes[test.pos + i]
			System.err.println("$i:  ${eyreByte.hex8}  ${nasmByte.hex8}  ${eyreByte.bin233}  ${nasmByte.bin233}")
		}
		for(i in 0 ..< 4)
			System.err.println("        ${nasmBytes[test.pos + test.size + i].hex8}")
		error("Testing failed")
	}



	fun test() {
		val nasmBuilder = StringBuilder()
		nasmBuilder.appendLine("bits 64")
		val context = Context(emptyList(), Paths.get("build"))
		val assembler = Assembler(context)
		val tests = ArrayList<EncTest>()

		for(enc in EncGen.parser.encs) {
			if(!enc.shouldTest()) continue

			val ins = InsNode(
				SrcPos(),
				enc.mnemonic,
				randomOp(enc.op1),
				randomOp(enc.op2),
				randomOp(enc.op3),
				randomOp(enc.op4),
			)

			try {
				assembler.assembleIns(ins)
				tests += EncTest(ins, enc, ins.pos.disp, ins.size)
				assembler.insertZero()
				nasmBuilder.appendLine(ins.nasmString())
				nasmBuilder.appendLine("db 0")
			} catch(e: Exception) {
				System.err.println(enc.toString())
				System.err.println(NodeStrings.string(ins))
				e.printStackTrace()
				exitProcess(1)
			}
		}

		val nasmInputPath = Paths.get("test.asm")
		Files.writeString(nasmInputPath, nasmBuilder.toString())
		Util.run("nasm", "-fwin64", "test.asm")
		Files.delete(nasmInputPath)
		val nasmOutputPath = Paths.get("test.obj")
		val reader = BinReader(Files.readAllBytes(nasmOutputPath))
		Files.delete(nasmOutputPath)
		reader.pos = 20
		if(reader.ascii(5) != ".text") error("NASM error")
		val nasmBytes = reader.bytes(reader.i32(40), reader.i32(36))
		val eyreBytes = context.textWriter.copy()

		for(test in tests) {
			println(NodeStrings.string(test.node))
			if(eyreBytes.size < test.pos + test.size)
				error("Out of bounds")
			for(i in 0 ..< test.size)
				if(nasmBytes[test.pos + i] != eyreBytes[test.pos + i])
					printDebug(test, nasmBytes, eyreBytes)
			if(nasmBytes[test.pos + test.size] != 0.toByte())
				printDebug(test, nasmBytes, eyreBytes)
		}
	}




}