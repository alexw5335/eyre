package eyre


// Super classes



interface Node {
	val srcPos: SrcPos?
}

interface Symbol {
	val parent: Symbol
	val name: Name
	var resolved: Boolean get() = false; set(_) { }
	val isAnon get() = name.id == 0
}

interface ScopedSym : Symbol {
	val scope: Symbol get() = this
}

interface SizedSym : Symbol {
	val size: Int
}

interface TypedSym : SizedSym {
	val type: Type
	override val size get() = type.size
}

interface IntSym : Symbol {
	val intValue: Int
}

interface SymNode : Node {
	var sym: Symbol?
}

sealed interface StructChild : Node, SizedSym {
	var structIndex: Int
	var structOffset: Int
}



// Simple nodes



object NullNode : Node { override val srcPos = null }

class NameNode(override val srcPos: SrcPos?, val value: Name, override var sym: Symbol? = null) : SymNode

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
	val right: Node,
	override var sym: Symbol? = null
) : SymNode

class MemberNode(
	override val srcPos: SrcPos?,
	override val parent: StructNode,
	override val name: Name,
	val typeNode: TypeNode
) : StructChild, Symbol, TypedSym {
	override var type: Type = NullType
	override var resolved = false
	override var structIndex = 0
	override var structOffset = 0
}

class StructNode(
	override val srcPos: SrcPos?,
	override val parent: Symbol,
	override val name: Name,
	val isUnion: Boolean,
) : StructChild, ScopedSym, Type {
	val members = ArrayList<StructChild>()
	override var resolved = false
	override var structIndex = 0
	override var structOffset = 0
	override var size = 0
	override var alignment = 0
}

class EnumNode(
	override val srcPos: SrcPos?,
	override val parent: Symbol,
	override val name: Name
) : Node, ScopedSym {
	override var resolved = false
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
	var typeNode: TypeNode
) : Node, Type {
	var type: Type = NullType
	override val size get() = type.size
	override val alignment get() = type.alignment
}



class TypeNode(
	override val srcPos: SrcPos?,
	val names: List<Name>,
	val arraySizes: List<Node>
) : Node {
	var type: Type? = null
}



class RefNode(
	override val srcPos: SrcPos?,
	val left: Node,
	val right: Node
) : Node {
	var receiver: Symbol? = null
	var intSupplier: (() -> Int)? = null
}



// TYPES



interface Type : SizedSym {
	val alignment: Int
}

object NullType : Type {
	override val parent = RootSym
	override val name = Names.NONE
	override val size = 0
	override val alignment = 0
}

class IntType(override val name: Name, override val size: Int) : Type {
	override val parent = RootSym
	override val alignment = size
}

class ArrayType(val base: Type, var count: Int = 0): Type {
	override val name = base.name
	override val parent = base.parent
	override val size get() = count * base.size
	override val alignment get() = base.alignment
}

object Types {
	val BYTE = IntType(Names.BYTE, 1)
	val WORD = IntType(Names.WORD, 2)
	val DWORD = IntType(Names.DWORD, 4)
	val QWORD = IntType(Names.QWORD, 8)
}