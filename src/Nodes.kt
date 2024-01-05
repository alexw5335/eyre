package eyre


// Super classes



abstract class Node {
	var srcPos: SrcPos? = null
}

interface Symbol {
	val place: Place
	val parent get() = place.parent
	val name get() = Names[place.name]
}

interface ScopedSym : Symbol {
	val scope: Int get() = place.id
}



// Simple nodes



object NullNode : Node()

class NameNode(val value: Name) : Node()

class IntNode(val value: Int) : Node()

class StringNode(val value: String) : Node()

class RegNode(val value: Reg) : Node()

class UnNode(val op: UnOp, val child: Node) : Node()

class BinNode(val op: BinOp, val left: Node, val right: Node) : Node()

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



// Symbol nodes



class RootSym : Symbol {
	override val place = Place(0, 0, 0)
}

class NamespaceNode(override val place: Place) : Node(), Symbol

class LabelNode(override val place: Place) : Node(), Symbol

class ProcNode(override val place: Place) : Node(), ScopedSym

class ScopeEndNode(val symbol: Node) : Node()

class InsNode(
	val mnemonic: Mnemonic,
	val op1: OpNode,
	val op2: OpNode,
	val op3: OpNode,
	val op4: OpNode
) : Node() {
	val count = when {
		op1 == OpNode.NONE -> 0
		op2 == OpNode.NONE -> 1
		op3 == OpNode.NONE -> 2
		op4 == OpNode.NONE -> 3
		else -> 4
	}
}