package eyre



interface Node {
	val srcPos: SrcPos?
}



data object NullNode : Node { override val srcPos = null }

class RegNode(override val srcPos: SrcPos?, val value: Reg) : Node

class NameNode(override val srcPos: SrcPos?, val value: Name, var sym: Sym? = null) : Node

class IntNode(override val srcPos: SrcPos?, val value: Long) : Node

class StringNode(override val srcPos: SrcPos?, val value: String) : Node

class UnNode(override val srcPos: SrcPos?, val op: UnOp, val child: Node) : Node

class BinNode(override val srcPos: SrcPos?, val op: BinOp, val left: Node, val right: Node) : Node



fun UnNode.calc(regValid: Boolean, function: (Node, Boolean) -> Long): Long = op.calc(
	function(child, op.regValid && regValid)
)

fun BinNode.calc(regValid: Boolean, function: (Node, Boolean) -> Long): Long = op.calc(
	function(left, op.leftRegValid && regValid),
	function(right, op.rightRegValid && regValid)
)



class EnumEntryNode(
	override val srcPos: SrcPos?,
	val sym: EnumEntrySym,
	val value: Node?
) : Node



class EnumNode(
	override val srcPos: SrcPos?,
	val sym: EnumSym,
	val entries: List<EnumEntryNode>
) : Node



class StructNode(
	override val srcPos: SrcPos?,
	val sym: StructSym,
	val members: List<MemberNode>
) : Node



class MemberNode(
	override val srcPos: SrcPos?,
	val sym: MemberSym,
	val typeNode: TypeNode?,
	val struct: StructNode?
) : Node



class DllCallNode(
	override val srcPos: SrcPos?,
	val dllName: Name,
	val name: Name,
	var sym: DllImportSym? = null
) : Node



class InsNode(
	override val srcPos: SrcPos?,
	val mnemonic: Mnemonic,
	val op1: OpNode?,
	val op2: OpNode?,
	val op3: OpNode?
) : Node {
	val count = when {
		op1 == null -> 0
		op2 == null -> 1
		op3 == null -> 2
		else -> 3
	}
}

class OpNode(
	override val srcPos: SrcPos,
	val type: OpType,
	val width: Width,
	val child: Node?,
	val reg: Reg,
) : Node

class TypedefNode(
	override val srcPos: SrcPos,
	val sym: TypedefSym,
	val typeNode: TypeNode
) : Node

class LabelNode(
	override val srcPos: SrcPos,
	val sym: LabelSym
) : Node

class ProcNode(
	override val srcPos: SrcPos,
	val sym: ProcSym,
	val children: List<Node>
) : Node


class NamespaceNode(
	override val srcPos: SrcPos?,
	val sym: NamespaceSym,
	val children: List<Node>
) : Node



class TypeNode(
	override val srcPos: SrcPos?,
	val names: List<Name>,
	val arraySizes: List<Node>,
	var type: Type? = null
) : Node

class RefNode(
	override val srcPos: SrcPos?,
	val left: Node,
	val right: Node
) : Node {
	var receiver: Sym? = null
	var intSupplier: (() -> Int)? = null
}

class InitNode(
	override val srcPos: SrcPos?,
	val elements: List<Node>
) : Node

class ArrayNode(
	override val srcPos: SrcPos?,
	val left: Node,
	val right: Node
) : Node

class DotNode(
	override val srcPos: SrcPos?,
	val left: Node,
	val right: Node,
	var sym: Sym? = null
) : Node

class CallNode(
	override val srcPos: SrcPos?,
	val left: Node,
	val elements: List<Node>
) : Node