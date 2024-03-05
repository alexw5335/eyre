package eyre



class Base(
	val srcPos: SrcPos? = null,
	val parent: Sym? = null,
	val name: Name = Name.NONE
) {
	var resolved = false
	var sec = Section.NULL
	var disp = 0
	companion object { val NULL = Base() }
}



/*
Interfaces
 */



interface Node {
	val base: Base
	val srcPos get() = base.srcPos
}

interface Sym : Node {
	val parent get() = base.parent
	val name get() = base.name
	var resolved get() = base.resolved; set(value) { base.resolved = value }
}

interface AnonSym : Sym {
	override val base get() = Base.NULL
}

interface IntSym : Sym {
	val intValue: Long
}

interface PosSym : Sym, Pos {
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

interface SymNode : Node {
	val sym: Sym?
}

interface MemSym : Sym {
	val mem: Mem
}


/*
Basic nodes
 */

class OpNode(
	override val base: Base,
	val type: OpType,
	val width: Width,
	val child: Node?,
	val reg: Reg,
) : Node

class RegNode(override val base: Base, val value: Reg) : Node

class NameNode(override val base: Base, val value: Name, override var sym: Sym? = null) : SymNode

class IntNode(override val base: Base, val value: Long) : Node

class StringNode(override val base: Base, val value: String, var litSym: StringLitSym? = null) : Node

class UnNode(override val base: Base, val op: UnOp, val child: Node) : Node {
	fun calc(function: (Node) -> Long): Long =
		op.calc(function(child))
	fun calc(regValid: Boolean, function: (Node, Boolean) -> Long): Long =
		op.calc(function(child, op.regValid && regValid))
}

class BinNode(override val base: Base, val op: BinOp, val left: Node, val right: Node) : Node {
	fun calc(function: (Node) -> Long): Long =
		op.calc(function(left), function(right))
	fun calc(regValid: Boolean, function: (Node, Boolean) -> Long): Long =
		op.calc(function(left, op.leftRegValid && regValid), function(right, op.rightRegValid && regValid))
}

class CallNode(
	override val base: Base,
	val left: Node,
	val elements: List<Node>
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
	override var sym: Sym? = null
) : SymNode

class DotNode(
	override val base: Base,
	val left: Node,
	val right: Node,
	override var sym: Sym? = null
) : SymNode

class TypeNode(
	override val base: Base,
	val names: List<Name>,
	val mods: List<Mod>,
	var type: Type? = null
) : Node {
	sealed interface Mod
	class ArrayMod(val sizeNode: Node?, var inferredSize: Int = -1) : Mod
	data object PointerMod : Mod
}

class DllImportNode(
	override val base: Base,
	val dllName: Name,
	val import: DllImport
) : Node, Sym, Pos by import

class IfNode(
	override val base: Base,
	val condition: Node?,
	val parentIf: IfNode?
) : Node, Sym {
	var startJmpPos = 0
	var endJmpPos = 0
	var next: IfNode? = null
}

class ForNode(override val base: Base, val range: Node) : Node, Sym {
	lateinit var index: VarNode
}

class DoWhileNode(override val base: Base) : Node, Sym {
	lateinit var condition: Node
}



/*
Top-level nodes
 */



class InsNode(
	override val base: Base,
	val mnemonic: Mnemonic,
	val op1: OpNode?,
	val op2: OpNode?,
	val op3: OpNode?
) : Node

class ScopeEndNode(val sym: Sym) : Node {
	override val base: Base = Base.NULL
}

class LabelNode(override val base: Base) : PosSym

class NamespaceNode(override val base: Base) : Sym

class ConstNode(
	override val base: Base,
	val valueNode: Node?,
	override var intValue: Long = 0
) : Node, IntSym

class VarNode(
	override val base: Base,
	val typeNode: TypeNode?,
	val valueNode: Node?,
	val proc: ProcNode?,
	override var type: Type = UnchosenType,
	var size: Int = 0,
	var mem: Mem? = null,
) : TypedSym {
	val isLocal get() = proc != null
}

class MemberNode(
	override val base: Base,
	val typeNode: TypeNode?,
	val struct: StructNode?,
	override var type: Type = UnchosenType,
	var index: Int = 0,
	var offset: Int = 0
) : Node, TypedSym, IntSym {
	override val intValue get() = offset.toLong()
}

class StructNode(
	override val base: Base,
	val isUnion: Boolean,
	override var size: Int = 0,
	override var alignment: Int = 0,
	val members: ArrayList<MemberNode> = ArrayList()
) : Type

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

class ProcNode(override val base: Base, var size: Int = 0) : PosSym {
	var mostParams = 0
	var paramsStackSize = 0
	var paramsStackPos = 0
	var localsStackSize = 0
	var regsStackSize = 0
	var stackSize = 0
}



/*
Symbols/Types
 */



class PosRefSym(
	val receiver: Pos,
	override val type: Type,
	val offsetSupplier: () -> Int
) : AnonSym, Pos, TypedSym {
	override val sec get() = receiver.sec
	override val disp get() = receiver.disp + offsetSupplier()
}

data object UnchosenType : Type {
	override val base = Base()
	override var size = 0
	override var alignment = 0
}

class StringLitSym(val value: String) : Sym, PosSym {
	override val base = Base()
}

class IntType(name: Name, override val size: Int, val signed: Boolean) : Type {
	override val base = Base(null, null, name)
	override val alignment = size
}

class StringType(override var size: Int = 0) : Type {
	override val base = Base(null, null, Name["rawstring"])
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

object IntTypes {
	val BYTE  = IntType(Name["byte"], 1, true)
	val WORD  = IntType(Name["word"], 2, true)
	val DWORD = IntType(Name["dword"], 4, true)
	val QWORD = IntType(Name["qword"], 8, true)
	val I8    = IntType(Name["i8"], 1, true)
	val I16   = IntType(Name["i16"], 2, true)
	val I32   = IntType(Name["i32"], 4, true)
	val I64   = IntType(Name["i64"], 8, true)
	val U8    = IntType(Name["u8"], 1, false)
	val U16   = IntType(Name["u16"], 2, false)
	val U32   = IntType(Name["u32"], 4, false)
	val U64   = IntType(Name["u64"], 8, false)
	val ALL   = arrayOf(BYTE, WORD, DWORD, QWORD, I8, I16, I32, I64, U8, U16, U32, U64)
}