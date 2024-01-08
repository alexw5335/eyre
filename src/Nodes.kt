package eyre


// Super classes



abstract class Node {
	var srcPos: SrcPos? = null
}

interface Symbol {
	val parent: Symbol
	val name: Name
	var resolved: Boolean get() = false; set(_) { }
}

interface ScopedSym : Symbol {
	val scope: Symbol get() = this
}

interface IntSym : Symbol {
	val intValue: Int
}



// Simple nodes



object NullNode : Node()

class NameNode(val value: Name, var symbol: Symbol? = null) : Node()

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



fun UnNode.calc(function: (Node) -> Int) = op.calc(function(child))

fun BinNode.calc(function: (Node) -> Int) = op.calc(function(left), function(right))



// Symbol nodes



class EnumNode(
	override val parent: Symbol,
	override val name: Name,
	val entries: ArrayList<EnumEntryNode> = ArrayList()
) : Node(), ScopedSym

class EnumEntryNode(
	override val parent: EnumNode,
	override val name: Name
) : Node(), IntSym {
	override var intValue = 0
}

class RootSym : Symbol {
	override val parent = this
	override val name = Names.NONE
}

class NamespaceNode(
	override val parent: Symbol,
	override val name: Name
) : Node(), ScopedSym

class LabelNode(
	override val parent: Symbol,
	override val name: Name
) : Node(), Symbol

class ProcNode(
	override val parent: Symbol,
	override val name: Name
) : Node(), ScopedSym

class ConstNode(
	override val parent: Symbol,
	override val name: Name,
	val valueNode: Node
) : Node(), Symbol, IntSym {
	override var intValue = 0
	override var resolved = false
}

class ScopeEndNode(val origin: Node) : Node()

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