package eyre



class Base(
	val srcPos: SrcPos? = null,
	val parent: Sym? = null,
	val name: Name = Name.NONE
) {
	var resolved = false
	var sec: Section = Section.NULL
	var disp: Int = 0
	companion object { val NULL = Base() }
}


interface NodeOrSym {
	val base: Base
}

interface Node : NodeOrSym {
	val srcPos get() = base.srcPos
}

interface Sym : NodeOrSym {
	val parent get() = base.parent
	val name get() = base.name
	var resolved get() = base.resolved; set(value) { base.resolved = value }
}

interface AnonSym : Sym {
	override val base get() = Base.NULL
}

interface IntSym : Sym {
	var intValue: Long
}

interface PosSym : Sym {
	val sec: Section
	val disp: Int
	val addr get() = sec.addr + disp
}

interface MutPosSym : PosSym {
	override var sec: Section get() = base.sec; set(value) { base.sec = value }
	override var disp: Int get() = base.disp; set(value) { base.disp = value }
}

interface SizedSym : Sym {
	val size: Int
}

interface Type : SizedSym {
	val alignment: Int
}

interface TypedSym : Sym {
	val type: Type
}

/** Indicates that a type has not yet been resolved. */
data object NullType : Type, AnonSym {
	override var size = 0
	override var alignment = 0
}

class PosRefSym(
	val receiver: PosSym,
	override val type: Type,
	val offsetSupplier: () -> Int
) : AnonSym, PosSym, TypedSym {
	override val sec get() = receiver.sec
	override val disp get() = receiver.disp + offsetSupplier()
}


data object NullNode : Node { override val base = Base.NULL }

class RegNode(override val base: Base, val value: Reg) : Node

class NameNode(override val base: Base, val value: Name, var sym: Sym? = null) : Node

class IntNode(override val base: Base, val value: Long) : Node

class StringNode(override val base: Base, val value: String, var litPos: Pos? = null) : Node

class UnNode(override val base: Base, val op: UnOp, val child: Node) : Node

class BinNode(override val base: Base, val op: BinOp, val left: Node, val right: Node) : Node



fun UnNode.calc(function: (Node) -> Long): Long = op.calc(function(child))

fun BinNode.calc(function: (Node) -> Long): Long = op.calc(function(left), function(right))

fun UnNode.calc(regValid: Boolean, function: (Node, Boolean) -> Long): Long = op.calc(
	function(child, op.regValid && regValid)
)

fun BinNode.calc(regValid: Boolean, function: (Node, Boolean) -> Long): Long = op.calc(
	function(left, op.leftRegValid && regValid),
	function(right, op.rightRegValid && regValid)
)



class ConstNode(
	override val base: Base,
	val valueNode: Node?,
	override var intValue: Long = 0
) : Node, IntSym



class MemberNode(
	override val base: Base,
	val typeNode: TypeNode?,
	val struct: StructNode?,
	override var type: Type = NullType,
	var index: Int = 0,
	var offset: Int = 0
) : Node, TypedSym

class StructNode(
	override val base: Base,
	val isUnion: Boolean,
	override var size: Int = 0,
	override var alignment: Int = 0,
	val members: ArrayList<MemberNode> = ArrayList()
) : Node, MutPosSym, Type

class VarNode(
	override val base: Base,
	val typeNode: TypeNode?,
	val valueNode: Node?,
	var type: Type = NullType,
	var size: Int = 0
) : Node, MutPosSym


class EnumEntryNode(
	override val base: Base,
	val valueNode: Node?,
	override var intValue: Long = 0
) : Node, IntSym



class EnumNode(
	override val base: Base,
	val entries: ArrayList<EnumEntryNode> = ArrayList(),
	override var size: Int = 0 ,
	override var alignment: Int = 0
) : Node, Type



class DllCallNode(
	override val base: Base,
	val dllName: Name,
	val name: Name,
	var importPos: Pos? = null
) : Node



class InsNode(
	override val base: Base,
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
	override val base: Base,
	val type: OpType,
	val width: Width,
	val child: Node?,
	val reg: Reg,
) : Node

class TypedefNode(
	override val base: Base,
	val typeNode: TypeNode?,
	var type: Type = NullType
) : Node, Type {
	override val size get() = type.size
	override val alignment get() = type.alignment
}

class LabelNode(
	override val base: Base,
) : Node, MutPosSym

class ProcNode(
	override val base: Base,
	val children: ArrayList<Node> = ArrayList<Node>(),
	var size: Int = 0
) : Node, MutPosSym

class NamespaceNode(
	override val base: Base,
	val children: ArrayList<Node> = ArrayList()
) : Node, Sym

class TypeNode(
	override val base: Base,
	val names: List<Name>,
	val arraySizes: List<Node>,
	var type: Type? = null
) : Node

class RefNode(
	override val base: Base,
	val left: Node,
	val right: Node
) : Node {
	var receiver: Sym? = null
	var intSupplier: (() -> Long)? = null
}

class InitNode(
	override val base: Base,
	val elements: List<Node>
) : Node

class ArrayNode(
	override val base: Base,
	val left: Node,
	val right: Node,
	var sym: Sym? = null
) : Node

class DotNode(
	override val base: Base,
	val left: Node,
	val right: Node,
	var sym: Sym? = null
) : Node

class CallNode(
	override val base: Base,
	val left: Node,
	val elements: List<Node>
) : Node



/*
Types
 */




class IntType(name: Name, override val size: Int) : Type {
	override val base = Base(null, null, name)
	override val alignment = size
}

class StringType(override var size: Int = 0) : Type {
	override val base = Base(null, null, Name.STRING)
	override var alignment = 8
}

class PointerType(val baseType: Type): Type {
	override val base = Base()
	override val size = 8
	override val alignment = 8
}

class ArrayType(
	val baseType: Type,
	var count: Int = 0
): Type {
	override val base = Base()
	override val size get() = count * baseType.size
	override val alignment get() = baseType.alignment
}

object Types {
	val BYTE = IntType(Name.BYTE, 1)
	val WORD = IntType(Name.WORD, 2)
	val DWORD = IntType(Name.DWORD, 4)
	val QWORD = IntType(Name.QWORD, 8)
}