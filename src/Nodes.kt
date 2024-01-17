package eyre


// Super classes



interface Node {
	val srcPos: SrcPos?
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



object NullNode : Node { override val srcPos = null }

class NameNode(override val srcPos: SrcPos?, val value: Name, var symbol: Symbol? = null) : Node

class IntNode(override val srcPos: SrcPos?, val value: Int) : Node

class StringNode(override val srcPos: SrcPos?, val value: String) : Node

class RegNode(override val srcPos: SrcPos?, val value: Reg) : Node

class UnNode(override val srcPos: SrcPos?, val op: UnOp, val child: Node) : Node

class BinNode(override val srcPos: SrcPos?, val op: BinOp, val left: Node, val right: Node) : Node

class OpNode(
	override val srcPos: SrcPos?,
	val type: OpType,
	val width: Width,
	val reg: Reg,
	val child: Node
) : Node



fun UnNode.calc(function: (Node) -> Int) = op.calc(function(child))

fun BinNode.calc(function: (Node) -> Int) = op.calc(function(left), function(right))



// Symbol nodes



class ArrayNode(
	override val srcPos: SrcPos?,
	val left: Node,
	val right: Node
) : Node

class DotNode(
	override val srcPos: SrcPos?,
	val left: Node,
	val right: Node
) : Node

class MemberNode(
	override val srcPos: SrcPos?,
	override val parent: StructNode,
	override val name: Name,
	val typeNode: Node
) : Node, Symbol {
	var type: Type = NullType
}

class StructNode(
	override val srcPos: SrcPos?,
	override val parent: Symbol,
	override val name: Name,
) : Node, ScopedSym {
	val members = ArrayList<MemberNode>()
}

class EnumNode(
	override val srcPos: SrcPos?,
	override val parent: Symbol,
	override val name: Name
) : Node, ScopedSym {
	val entries = ArrayList<EnumEntryNode>()
}

class EnumEntryNode(
	override val srcPos: SrcPos?,
	override val parent: EnumNode,
	override val name: Name,
	val valueNode: Node? = null
) : Node, IntSym {
	override var intValue = 0
	override var resolved = false
}

object RootSym : Symbol {
	override val parent = this
	override val name = Names.NONE
}

class NamespaceNode(
	override val srcPos: SrcPos?,
	override val parent: Symbol,
	override val name: Name
) : Node, ScopedSym

class LabelNode(
	override val srcPos: SrcPos?,
	override val parent: Symbol,
	override val name: Name
) : Node, Symbol

class ProcNode(
	override val srcPos: SrcPos?,
	override val parent: Symbol,
	override val name: Name
) : Node, ScopedSym

class ConstNode(
	override val srcPos: SrcPos?,
	override val parent: Symbol,
	override val name: Name,
	val valueNode: Node
) : Node, Symbol, IntSym {
	override var intValue = 0
	override var resolved = false
}

class ScopeEndNode(val origin: Node) : Node {
	override val srcPos = null
}

class InsNode(
	override val srcPos: SrcPos?,
	val mnemonic: Mnemonic,
	val op1: OpNode?,
	val op2: OpNode?,
	val op3: OpNode?,
	val op4: OpNode?
) : Node {
	val count = when {
		op1 == null -> 0
		op2 == null -> 1
		op3 == null -> 2
		op4 == null -> 3
		else        -> 4
	}
}

class TypedefNode(
	override val srcPos: SrcPos?,
	override val parent: Symbol,
	override val name: Name,
	var typeNode: Node
) : Node, Type {
	var type: Type = NullType
	override val size get() = type.size
}



// TYPES



interface Type : Symbol {
	val size: Int
}

object NullType : Type {
	override val parent = RootSym
	override val name = Names.NULL
	override val size = 0
}

class IntType(override val name: Name, override val size: Int) : Type {
	override val parent = RootSym
}

class ArrayType(val base: Type, var count: Int = 0): Type {
	override val name = base.name
	override val parent = base.parent
	override val size get() = count * base.size
}



object Types {
	val BYTE = IntType(Names.BYTE, 1)
	val WORD = IntType(Names.WORD, 2)
	val DWORD = IntType(Names.DWORD, 4)
	val QWORD = IntType(Names.QWORD, 8)
}