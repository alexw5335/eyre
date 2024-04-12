package eyre



/*
Interfaces
 */



enum class GenType(val isLeaf: Boolean = false) {
	NONE(true),
	/** Immediate operand, treated like I32 in most cases. */
	I8(true),
	/** Immediate operand. */
	I32(true),
	/** Register operand <- qword or unsigned dword immediate, initialised with `REX.W B8+r MOV R64, I64`. */
	I64(false),
	/** Memory operands. */
	SYM8(true),
	SYM16(true),
	SYM32(true),
	SYM64(true),
	/** Loads into qword registers using MOVZX or MOVSX. */
	SYM_U8,
	SYM_U16,
	SYM_U32,
	SYM_I8,
	SYM_I16,
	SYM_I32,
	/** Nodes with children */
	UNARY_LEAF,
	UNARY_NODE,
	BINARY_NODE_NODE_LEFT,
	BINARY_NODE_NODE_RIGHT,
	BINARY_NODE_LEAF,
	BINARY_LEAF_NODE,
	BINARY_LEAF_LEAF(false),
	BINARY_LEAF_NODE_COMMUTATIVE(false),
	BINARY_LEAF_LEAF_COMMUTATIVE(true);

	/**
	 * If this element can be represented by an immediate operand
	 */
	val isImm get() = this == I8 || this == I32
}

sealed class Node {
	var srcPos: SrcPos? = null
	var exprType: Type? = null
	var exprSym: Sym? = null
	var resolved = false

	var isLeaf = false
	var numRegs = 0
	var isConst = false
	var constValue = 0L
	var genType = GenType.NONE
}

interface Sym {
	val parent: Sym?
	val name: Name
}

interface AnonSym : Sym {
	override val parent get() = null
	override val name get() = Name.NONE
}

interface Type : Sym {
	val size: Int
	val alignment: Int
	val isQword get() = size == 8
	val signed get() = false
}

interface TypedSym : Sym {
	val type: Type
}



/*
Basic nodes
 */



class IntNode(val value: Long) : Node()

class StringNode(val value: String, var litSym: StringLitSym? = null) : Node()

class UnNode(val op: UnOp, val child: Node) : Node() {
	inline fun calc(function: (Node) -> Long): Long = op.calc(function(child))
}

class BinNode(val op: BinOp, val left: Node, val right: Node) : Node() {
	inline fun calc(function: (Node) -> Long): Long = op.calc(function(left), function(right))
}

class NameNode(val name: Name) : Node()

class CallNode(val left: Node, val args: List<Node>) : Node() {
	var receiver: FunNode? = null
	var mem: Operand? = null
}

class ArrayNode(val left: Node, val right: Node) : Node() {
	var type: Type? = null
}

class DotNode(val left: Node, val right: Node) : Node() {
	enum class Type {
		SYM,
		MEMBER,
		DEREF
	}
	var type = Type.SYM
	var member: MemberNode? = null
}

class RefNode(val left: Node, val right: Node) : Node()

class InitNode(val elements: List<Node>) : Node()

class TypeNode(val names: List<Name>, val mods: List<Mod>, var type: Type? = null) : Node() {
	sealed interface Mod
	class ArrayMod(val sizeNode: Node?, var inferredSize: Int = -1) : Mod
	data object PointerMod : Mod
}

class DllImportNode(val dllName: Name, val import: DllImport) : Node(), AnonSym



/*
Top-level nodes
 */



class ScopeEndNode(val sym: Sym) : Node()

class NamespaceNode(override val parent: Sym?, override val name: Name) : Node(), Sym

class ConstNode(
	override val parent: Sym?,
	override val name: Name,
	val valueNode: Node,
	var intValue: Long = 0
) : Node(), Sym

class VarNode(
	override val parent: Sym?,
	override val name: Name,
	val typeNode: TypeNode?,
	val valueNode: Node?,
	override var type: Type = UnchosenType,
) : Node(), TypedSym {
	val size get() = type.size
	var loc: VarLoc? = null
}

class MemberNode(
	override val parent: Sym?,
	override val name: Name,
	val typeNode: TypeNode?,
	val struct: StructNode?,
	override var type: Type = UnchosenType,
	var index: Int = 0,
	var offset: Int = 0
) : Node(), TypedSym

class StructNode(
	override val parent: Sym?,
	override val name: Name,
	val isUnion: Boolean,
	override var size: Int = 0,
	override var alignment: Int = 0,
	val members: ArrayList<MemberNode> = ArrayList()
) : Node(), Type

class EnumEntryNode(
	override val parent: Sym?,
	override val name: Name,
	val valueNode: Node?,
	var intValue: Long = 0,
) : Node(), Sym

class EnumNode(
	override val parent: Sym?,
	override val name: Name,
) : Node(), Type {
	val entries = ArrayList<EnumEntryNode>()
	override var size = 0
	override var alignment = 0
}

class FunNode(
	override val parent: Sym?,
	override val name: Name
) : Node(), Sym {
	val pos = SecPos()
	var returnTypeNode: TypeNode? = null
	var returnType: Type? = null
	var size = 0
	val params = ArrayList<VarNode>()
	val locals = ArrayList<VarNode>()
	var mostParams = 0
	var stackPos = 0
	/**
	 * Offset from RBP for the first arg.
	 * E.g. arg 4: RBP - 64. Arg 5: RBP - 56. Arg 6: RBP - 48, etc.
	 */
	var argsOffset = 0
}



/*
Symbols
 */


class StringLitSym(val value: String) : AnonSym {
	val pos = SecPos()
}



/*
Types
 */



data object VoidType : Type, AnonSym {
	override var size = 0
	override var alignment = 0
}

data object UnchosenType : Type, AnonSym {
	override var size = 0
	override var alignment = 0
}

class IntType(override val name: Name, override val size: Int, override val signed: Boolean) : Type {
	override val parent = null
	override val alignment = size
	override fun toString() = name.string
}

class PointerType(override val type: Type): Type, TypedSym, AnonSym {
	override val size = 8
	override val alignment = 8
}

class ArrayType(override val type: Type, var count: Int = 0): Type, TypedSym, AnonSym {
	override val size get() = count * type.size
	override val alignment get() = type.alignment
}

object IntTypes {
	val INT   = IntType(Name["int"], 4, true)
	val I8    = IntType(Name["i8"], 1, true)
	val I16   = IntType(Name["i16"], 2, true)
	val I32   = IntType(Name["i32"], 4, true)
	val I64   = IntType(Name["i64"], 8, true)
	val U8    = IntType(Name["u8"], 1, false)
	val U16   = IntType(Name["u16"], 2, false)
	val U32   = IntType(Name["u32"], 4, false)
	val U64   = IntType(Name["u64"], 8, false)
	val ALL   = arrayOf(I8, I16, I32, I64, U8, U16, U32, U64, INT)
}