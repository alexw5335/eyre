package eyre.gen

import eyre.*
import java.nio.file.Paths
import kotlin.random.Random
import kotlin.system.exitProcess

object EncTesting {


	private fun randomMem(width: Width?, vm: Reg? = null): OpNode {
		val index = BinNode(
			BinOp.MUL,
			RegNode(vm ?: Reg.r64(Random.nextInt(16)).let { if(it.isInvalidIndex) Reg.RAX else it }),
			IntNode(1L shl Random.nextInt(3))
		)

		val base = RegNode(Reg.r64(Random.nextInt(16)).let { if(it.value == 5) Reg.RAX else it })
		val disp = IntNode(Random.nextInt(512).toLong())
		fun add(a: Node, b: Node) = BinNode(BinOp.ADD, a, b)

		val node = when(Random.nextInt(7)) {
			0 -> base
			1 -> index
			2 -> disp
			3 -> add(base, index)
			4 -> add(base, disp)
			5 -> add(index, disp)
			6 -> add(base, add(index, disp))
			else -> error("")
		}

		return OpNode.mem(node, width)
	}



	private fun randomImm(width: Width) =
		OpNode.imm(IntNode(Random.nextLong(width.min, width.max)), null)



	private fun randomOp(op: Op): OpNode = when(op) {
		Op.NONE  -> OpNode.NONE
		Op.R8    -> OpNode.reg(Reg.r8(Random.nextInt(16).let { if(it in 4..7) it + 4 else it }))
		Op.R16   -> OpNode.reg(Reg.r16(Random.nextInt(16)))
		Op.R32   -> OpNode.reg(Reg.r32(Random.nextInt(16)))
		Op.R64   -> OpNode.reg(Reg.r64(Random.nextInt(16)))
		Op.M8    -> randomMem(Width.BYTE)
		Op.M16   -> randomMem(Width.WORD)
		Op.M32   -> randomMem(Width.DWORD)
		Op.M64   -> randomMem(Width.QWORD)
		Op.M80   -> randomMem(Width.TWORD)
		Op.M128  -> randomMem(Width.XWORD)
		Op.M256  -> randomMem(Width.YWORD)
		Op.M512  -> randomMem(Width.ZWORD)
		Op.MEM   -> randomMem(null)
		Op.MM    -> OpNode.reg(Reg.mm(Random.nextInt(8)))
		Op.X     -> OpNode.reg(Reg.x(Random.nextInt(16)))
		Op.Y     -> OpNode.reg(Reg.y(Random.nextInt(16)))
		Op.DX    -> OpNode.reg(Reg.DX)
		Op.CL    -> OpNode.reg(Reg.CL)
		Op.AL    -> OpNode.reg(Reg.AL)
		Op.AX    -> OpNode.reg(Reg.AX)
		Op.EAX   -> OpNode.reg(Reg.EAX)
		Op.RAX   -> OpNode.reg(Reg.RAX)
		Op.I8    -> randomImm(Width.BYTE)
		Op.I16   -> randomImm(Width.WORD)
		Op.I32   -> randomImm(Width.DWORD)
		Op.I64   -> randomImm(Width.QWORD)
		Op.REL8  -> randomImm(Width.BYTE)
		Op.REL32 -> randomImm(Width.DWORD)
		Op.FS    -> OpNode.reg(Reg.FS)
		Op.GS    -> OpNode.reg(Reg.GS)
		Op.SEG   -> OpNode.reg(Reg.seg(Random.nextInt(6)))
		Op.CR    -> OpNode.reg(Reg.cr(Random.nextInt(9)))
		Op.DR    -> OpNode.reg(Reg.dr(Random.nextInt(8)))
		Op.ONE   -> OpNode.imm(IntNode(1), null)
		Op.ST    -> OpNode.reg(Reg.st(Random.nextInt(8)))
		Op.ST0   -> OpNode.reg(Reg.ST0)
		Op.VM32X -> randomMem(Width.DWORD, Reg.x(Random.nextInt(16)))
		Op.VM64X -> randomMem(Width.QWORD, Reg.x(Random.nextInt(16)))
		Op.VM32Y -> randomMem(Width.DWORD, Reg.y(Random.nextInt(16)))
		Op.VM64Y -> randomMem(Width.QWORD, Reg.y(Random.nextInt(16)))
		Op.VM32Z -> randomMem(Width.DWORD, Reg.z(Random.nextInt(16)))
		Op.VM64Z -> randomMem(Width.QWORD, Reg.z(Random.nextInt(16)))

		Op.A,
		Op.R,
		Op.M,
		Op.I,
		Op.RM,
		Op.XM,
		Op.YM,
		Op.MMM,
		Op.RM8,
		Op.RM16,
		Op.RM32,
		Op.RM64,
		Op.MMM64,
		Op.XM8,
		Op.XM16,
		Op.XM32,
		Op.XM64,
		Op.XM128,
		Op.YM8,
		Op.YM16,
		Op.YM32,
		Op.YM64,
		Op.YM128,
		Op.YM256 -> error("Unexpected compound operand: $op")
	}



	fun test() {
		val nasmBuilder = StringBuilder()
		nasmBuilder.appendLine("BITS 64")
		val context = Context(emptyList(), Paths.get("build"))
		val assembler = Assembler(context)

		for(enc in EncGen.parser.allEncs) {
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
			} catch(e: Exception) {
				System.err.println(enc.toString())
				System.err.println(NodeStrings.string(ins))
				e.printStackTrace()
				exitProcess(1)
			}
		}
	}




}