package eyre



/*
Interfaces
 */



sealed interface Node

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
	override var pos = Pos.NULL
	override var resolved = false
}



class AnonPosSym(override var pos: Pos) : PosSym {
	override val place = Place()
	override var resolved = true
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

class RegNode(val value: Reg) : Node

class OpNode(val type: OpType, val reg: Reg, val node: Node, val width: Width) : Node {
	val isNone get() = this == NONE
	val isNotNone get() = this != NONE
	val isReg get() = type.isReg
	val isMem get() = type.isMem
	val isImm get() = type.isImm
	companion object {
		val NONE = OpNode(OpType.NONE, Reg.NONE, NullNode, Width.NONE)
		fun reg(reg: Reg) = OpNode(reg.type, reg, NullNode, reg.width)
		fun mem(node: Node, width: Width) = OpNode(OpType.MEM, Reg.NONE, node, width)
		fun imm(node: Node, width: Width) = OpNode(OpType.IMM, Reg.NONE, node, width)
	}
}



/*
Top-level nodes
 */



class Label(
	override val srcPos: SrcPos,
	override val place: Place
) : TopNode, PosSym {
	override var resolved = false
	override var pos = Pos.NULL
}



class Proc(
	override val srcPos: SrcPos,
	override val place: Place,
	override val thisScope: Scope,
) : TopNode, ScopedSym, PosSym, SizedSym {
	override var resolved = false
	override var pos = Pos.NULL
	override var size = 0
}


class InsNode(
	override val srcPos: SrcPos,
	val mnemonic: Mnemonic,
	val op1: OpNode = OpNode.NONE,
	val op2: OpNode = OpNode.NONE,
	val op3: OpNode = OpNode.NONE,
	val op4: OpNode = OpNode.NONE
) : TopNode, AnonSym, PosSym, SizedSym {
	override var pos = Pos.NULL
	override var size = 0

	val r1 get() = op1.reg
	val r2 get() = op2.reg
	val r3 get() = op3.reg
	val r4 get() = op4.reg

	val count = when {
		op1.isNone -> 0
		op2.isNone -> 1
		op3.isNone -> 2
		op4.isNone -> 3
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
