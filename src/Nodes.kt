package eyre



/*
Interfaces
 */



sealed interface Node

sealed interface OpNode : Node {
	val type: OpType
}

sealed interface TopNode : Node {
	val srcPos: SrcPos
}

sealed interface Sym {
	val place: Place
	var resolved: Boolean
	val scope get() = place.scope
	val name get() = place.name
}

sealed interface ScopedSym : Sym {
	val thisScope: Scope
}

sealed interface AnonSym : Sym {
	override val place get() = Place()
	override var resolved get() = true; set(_) { }
}

sealed interface PosSym : Sym {
	var pos: Pos
}

/**
 * A symbol that takes up a certain number of bytes in the final executable.
 * The [size] is only guaranteed to be valid after the assembler pass has
 * successfully completed. The [size] is mainly used to provide debugging
 * information, and should not be used as a constant.
 */
sealed interface SizedSym : Sym {
	val size: Int
}



/*
Symbols
 */



class DllImport(name: Name) : PosSym {
	override val place = Place(Scope.NULL, name)
	override var pos = Pos()
	override var resolved = false
}



/*
Expression nodes
 */



class ScopeEnd(override val srcPos: SrcPos, val sym: Sym?): TopNode

data object NullNode : Node

class IntNode(val value: Long) : Node

class StringNode(val value: String, var sym: Sym? = null) : Node

class UnNode(val op: UnOp, val node: Node) : Node

class BinNode(val op: BinOp, val left: Node, val right: Node) : Node

class NameNode(val value: Name, var sym: Sym? = null) : Node

class RegNode(val value: Reg) : OpNode {
	override val type = value.type
}

class MemNode(val width: Width?, val node: Node) : OpNode {
	override val type = OpType.MEM
}

class ImmNode(val width: Width?, val node: Node) : OpNode {
	override val type = OpType.IMM
}



/*
Top-level nodes
 */



class Label(
	override val srcPos: SrcPos,
	override val place: Place
) : TopNode, PosSym {
	override var resolved = false
	override var pos = Pos()
}



class Proc(
	override val srcPos: SrcPos,
	override val place: Place,
	override val thisScope: Scope,
) : TopNode, ScopedSym, PosSym, SizedSym {
	override var resolved = false
	override var pos = Pos()
	override var size = 0
}

class Ins(
	override val srcPos: SrcPos,
	val mnemonic: Mnemonic,
	val op1: OpNode?,
	val op2: OpNode?,
	val op3: OpNode?,
	val op4: OpNode?
) : TopNode, AnonSym, PosSym, SizedSym {
	override var pos = Pos()
	override var size = 0
	val count = when {
		op1 == null -> 0
		op2 == null -> 1
		op3 == null -> 2
		op4 == null -> 3
		else -> 4
	}
}



class Const(
	override val srcPos: SrcPos,
	override val place: Place,
	val valueNode: Node
) : TopNode, Sym {
	override var resolved = false
	var intValue = 0L
}



class Namespace(
	override val srcPos: SrcPos,
	override val place: Place,
	override val thisScope: Scope
) : TopNode, ScopedSym {
	override var resolved = false
}



/*
class Struct(
	override val srcPos: SrcPos,
	override val place: Place
) : TopNode, PosSym, SizedSym {
	override var size = 0
	override var pos = Pos()
	override var resolved = false
}*/



/*
Helper functions
 */



inline fun UnNode.calc(validity: Boolean, function: (Node, Boolean) -> Long): Long = op.calc(
	function(node, validity && (op == UnOp.POS))
)

inline fun UnNode.calc(function: (Node) -> Long): Long =
	op.calc(function(node))

inline fun BinNode.calc(validity: Boolean, function: (Node, Boolean) -> Long): Long = op.calc(
	function(left, validity && (op == BinOp.ADD || op == BinOp.SUB)),
	function(right, validity && (op == BinOp.ADD))
)

inline fun BinNode.calc(function: (Node) -> Long): Long =
	op.calc(function(left), function(right))
