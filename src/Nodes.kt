package eyre



class NodeInfo(
	val srcPos: SrcPos? = null,
	val parent: Sym? = null,
	val name: Name = Name.NONE,
	var resolved: Boolean = false
) {
	companion object {
		val NULL = NodeInfo()
	}
}



/*
Interfaces
 */



interface Node {
	val info: NodeInfo
	val srcPos get() = info.srcPos
}

interface Sym : Node {
	val parent get() = info.parent
	val name get() = info.name
	var resolved get() = info.resolved; set(value) { info.resolved = value }
	val unResolved get() = !info.resolved
}

interface IntSym : Sym {
	val intValue: Long
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

interface SymNode : Node {
	val sym: Sym?
	val type: Type?
}



/*
Basic nodes
 */



class IntNode(override val info: NodeInfo, val value: Long) : Node

class StringNode(override val info: NodeInfo, val value: String, var litSym: StringLitSym? = null) : Node

class UnNode(override val info: NodeInfo, val op: UnOp, val child: Node) : Node {
	inline fun calc(function: (Node) -> Long): Long = op.calc(function(child))
}

class BinNode(override val info: NodeInfo, val op: BinOp, val left: Node, val right: Node) : Node {
	inline fun calc(function: (Node) -> Long): Long = op.calc(function(left), function(right))
}

class NameNode(
	override val info: NodeInfo,
	val name: Name,
	override var sym: Sym? = null,
	override var type: Type? = null
) : SymNode

class CallNode(
	override val info: NodeInfo,
	val left: Node,
	val args: List<Node>
) : SymNode {
	/** The type returned when calling the [sym]. */
	override var type: Type? = null
	/** Must be callable, either a [VarNode] or a [FunNode]. */
	override var sym: Sym? = null
}
class ArrayNode(
	override val info: NodeInfo,
	val left: Node,
	val right: Node,
) : Node {
	var type: Type? = null
}

class DotNode(
	override val info: NodeInfo,
	val left: Node,
	val right: Node,
) : SymNode {
	override var sym: Sym? = null
	override var type: Type? = null
}

class RefNode(
	override val info: NodeInfo,
	val left: Node,
	val right: Node
) : Node

class InitNode(
	override val info: NodeInfo,
	val elements: List<Node>
) : Node

class TypeNode(
	override val info: NodeInfo,
	val names: List<Name>,
	val mods: List<Mod>,
	var type: Type? = null
) : Node {
	sealed interface Mod
	class ArrayMod(val sizeNode: Node?, var inferredSize: Int = -1) : Mod
	data object PointerMod : Mod
}

class DllImportNode(
	override val info: NodeInfo,
	val dllName: Name,
	val import: DllImport
) : Node, Sym



/*
Top-level nodes
 */



class ScopeEndNode(val sym: Sym) : Node {
	override val info: NodeInfo = NodeInfo.NULL
}

class NamespaceNode(override val info: NodeInfo) : Sym

class ConstNode(
	override val info: NodeInfo,
	val valueNode: Node,
	override var intValue: Long = 0
) : Node, IntSym

class VarNode(
	override val info: NodeInfo,
	val typeNode: TypeNode?,
	val valueNode: Node?,
	override var type: Type = UnchosenType,
) : TypedSym {
	val size get() = type.size
	var operand: MemOperand? = null
}

class MemberNode(
	override val info: NodeInfo,
	val typeNode: TypeNode?,
	val struct: StructNode?,
	override var type: Type = UnchosenType,
	var index: Int = 0,
	var offset: Int = 0
) : Node, TypedSym, IntSym {
	override val intValue get() = offset.toLong()
}

class StructNode(
	override val info: NodeInfo,
	val isUnion: Boolean,
	override var size: Int = 0,
	override var alignment: Int = 0,
	val members: ArrayList<MemberNode> = ArrayList()
) : Type

class EnumEntryNode(
	override val info: NodeInfo,
	val valueNode: Node?,
	override var intValue: Long = 0
) : Node, IntSym

class EnumNode(
	override val info: NodeInfo,
	val entries: ArrayList<EnumEntryNode> = ArrayList(),
	override var size: Int = 0,
	override var alignment: Int = 0
) : Node, Type

class FunNode(override val info: NodeInfo) : Sym {
	val pos = SecPos()
	var returnTypeNode: TypeNode? = null
	var returnType: Type? = null
	var size = 0
	val params = ArrayList<VarNode>()
	val locals = ArrayList<VarNode>()
	var mostParams = 0
	var stackPos = 0
}



/*
Symbols
 */


class StringLitSym(val value: String) : Sym {
	override val info = NodeInfo()
	val pos = SecPos()
}



/*
Types
 */



data object VoidType : Type {
	override val info = NodeInfo()
	override var size = 0
	override var alignment = 0
}

data object UnchosenType : Type {
	override val info = NodeInfo()
	override var size = 0
	override var alignment = 0
}

class IntType(name: Name, override val size: Int, val signed: Boolean) : Type {
	override val info = NodeInfo(null, null, name)
	override val alignment = size
}

class PointerType(override val type: Type): Type, TypedSym {
	override val info = NodeInfo()
	override val size = 8
	override val alignment = 8
}

class ArrayType(override val type: Type, var count: Int = 0): Type, TypedSym {
	override val info = NodeInfo()
	override val size get() = count * type.size
	override val alignment get() = type.alignment
}

object IntTypes {
	val I8    = IntType(Name["i8"], 1, true)
	val I16   = IntType(Name["i16"], 2, true)
	val I32   = IntType(Name["i32"], 4, true)
	val I64   = IntType(Name["i64"], 8, true)
	val U8    = IntType(Name["u8"], 1, false)
	val U16   = IntType(Name["u16"], 2, false)
	val U32   = IntType(Name["u32"], 4, false)
	val U64   = IntType(Name["u64"], 8, false)
	val ALL   = arrayOf(I8, I16, I32, I64, U8, U16, U32, U64)
}