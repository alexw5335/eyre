package eyre

abstract class Node {
	var srcPos: SrcPos? = null
}

class NameNode(val value: Name) : Node()

class IntNode(val value: Int) : Node()

class StringNode(val value: String) : Node()

class RegNode(val value: Reg) : Node()

class UnNode(val op: UnOp, val child: Node) : Node()

class BinNode(val op: BinOp, val left: Node, val right: Node) : Node()

class LabelNode(val name: Name) : Node()

object NullNode : Node()

class OpNode(
	val type: OpType,
	val width: Width,
	val reg: Reg,
	val child: Node
) : Node() {
	companion object {
		val NONE = OpNode(OpType.NONE, Width.NONE, Reg.NONE, NullNode)
		fun reg(reg: Reg) = OpNode(reg.type, reg.width, reg, NullNode)
		fun mem(width: Width, child: Node) = OpNode(OpType.MEM, width, Reg.NONE, NullNode)
		fun imm(width: Width, child: Node) = OpNode(OpType.IMM, width, Reg.NONE, NullNode)
	}
}

class InsNode(
	val mnemonic: Mnemonic,
	val op1: OpNode,
	val op2: OpNode,
	val op3: OpNode,
	val op4: OpNode
) : Node()