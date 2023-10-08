package eyre


// Type system is a mess


class Base {
	var srcPos: SrcPos? = null
	var scope     = Scopes.EMPTY
	var name      = Names.EMPTY
	var thisScope = Scopes.EMPTY
	var resolved  = false
	var resolving = false
	var pos       = 0
	var section   = Section.TEXT

	companion object {
		val EMPTY = Base().also { it.resolved = true }
		fun create(name: Name, resolved: Boolean) = Base().also { it.name = name; it.resolved = resolved }
	}
}

sealed interface NodeOrSym {
	val base: Base
}

sealed interface Node : NodeOrSym {
	var srcPos get() = base.srcPos; set(value) { base.srcPos = value }
}

sealed interface Sym : NodeOrSym {
	val scope get() = base.scope
	val name get() = base.name
	// If all compile-time constants that can be accessed by referencing this symbol have been calculated.
	var resolved get() = base.resolved; set(v) { base.resolved = v }
	var resolving get() = base.resolving; set(v) { base.resolving = v }
	val notResolved get() = !resolved

	val qualifiedName get() = if(scope.isEmpty) "$name" else "$scope.$name"
}

interface Type : SizedSym {
	val alignment get() = size
}

interface TypedSym : Sym {
	val type: Type
}

interface IntSym : Sym {
	val intValue: Long
}

interface ScopedSym : Sym {
	val thisScope get() = base.thisScope
}

/**
 * - [pos] and [section] should be set by the Assembler
 */
interface PosSym : Sym {
	var pos get() = base.pos; set(value) { base.pos = value }
	var section get() = base.section; set(value) { base.section = value }
}

/**
 * A symbol that defines an offset into some base symbol, typically another [OffsetSym] or a [PosSym].
 */
interface OffsetSym : Sym {
	val offset: Int
}

/**
 * [size] is only guaranteed to be set after the [Assembler] has run
 */
interface SizedSym : Sym {
	val size: Int
}



class ArrayType(val type: Type) : Type {
	override val base = Base.EMPTY
	var count = 0
	override val size get() = type.size * count
	override val alignment = type.alignment
}



interface IntType : Type



object ByteType : IntType {
	override val base = Base.create(Names["byte"], true)
	override val size = 1
}

object WordType : IntType {
	override val base = Base.create(Names["word"], true)
	override val size = 2
}

object DwordType : IntType {
	override val base = Base.create(Names["dword"], true)
	override val size = 4
}

object QwordType : IntType {
	override val base = Base.create(Names["qword"], true)
	override val size = 8
}

object VoidType : Type {
	override val base = Base.EMPTY
	override val size = 0
	override fun toString() = "VoidType"
}

class AnonPosSym(override var section: Section, override var pos: Int) : PosSym {
	override val base = Base.EMPTY
}

data object NullNode : Node {
	override val base = Base.EMPTY
}

class ScopeEnd(val sym: Sym?): Node {
	override val base = Base.EMPTY
}



class ImportNode(val names: Array<Name>) : Node {
	override val base = Base()
}



class PosRefSym(
	val receiver: PosSym,
	val offset: Int,
	override val type: Type
) : PosSym, TypedSym {
	override val base = Base.EMPTY
	override var pos
		set(_) = error("Cannot set ref symbol pos")
		get() = receiver.pos + offset
	override var section
		set(_) = error("Cannot set ref symbol section")
		get() = receiver.section
}



class DllImport(name: Name) : PosSym {
	override val base = Base().also {
		it.name = name
		it.section = Section.RDATA
	}
}



class Member(
	override val base: Base,
	val typeNode: TypeNode
) : Node, IntSym, TypedSym, OffsetSym {
	var size = 0
	override var type: Type = VoidType
	override var offset = 0
	override val intValue get() = offset.toLong()
	lateinit var parent: Struct
}



class Struct(override val base: Base, val members: List<Member>) : Node, ScopedSym, Type {
	override var size = 0
	override var alignment = 0
}



class EnumEntry(override val base: Base, val valueNode: Node?) : Node, Sym, IntSym {
	var value = 0L
	override val intValue get() = value
	lateinit var parent: Enum
}



class Const(override val base: Base, val valueNode: Node) : Node, IntSym {
	override var intValue = 0L
}



class Typedef(
	override val base: Base,
	val typeNode: TypeNode,
	var type: Type = VoidType
) : Node, Type {
	override val size get() = type.size
	override val alignment get() = type.alignment
}



class TypeNode(
	val name: Name?,
	val names: Array<Name>?,
	val arraySizes: Array<Node>?,
) : Node {
	override val base = Base()
	var type: Type? = null
}



class Enum(
	override val base: Base,
	val entries: ArrayList<EnumEntry>,
	val isBitmask: Boolean
) : Node, ScopedSym, Type {
	override var size = 0
}



class Var(
	override val base : Base,
	val typeNode      : TypeNode?,
	val valueNode     : Node?,
	val isVal         : Boolean
) : Node, PosSym, TypedSym, SizedSym {
	override var type: Type = VoidType
	override var size = 0
}



class Proc(override val base: Base, val parts: List<Node>): Node, ScopedSym, PosSym, SizedSym {
	override var size = 0
}



class InitNode(val values: List<Node>) : Node {
	override val base = Base()
}

class IndexNode(val index: Node) : Node {
	override val base = Base()
}

class Directive(val name: Name, val values: List<Node>) : Node {
	override val base = Base()
	var target: Node = NullNode
}

class Namespace(override val base: Base) : Node, ScopedSym

class Label(override val base: Base) : Node, PosSym

class RegNode(val value: Reg) : Node {
	override val base = Base()
}

/** [sym] is only for string literals in OpNodes */
class StringNode(val value: String, override var sym: Sym? = null) : Node, SymNode {
	override val base = Base()
}

class FloatNode(val value: Double) : Node {
	override val base = Base()
}

class IntNode(val value: Long) : Node {
	override val base = Base()
}

class UnNode(val op: UnOp, val node: Node) : Node {
	override val base = Base()
}

interface SymNode : Node {
	var sym: Sym?
}

class DotNode(
	val left: Node,
	val right: Name,
	override var sym: Sym? = null
) : SymNode {
	override val base = Base()
}

class ReflectNode(
	val left: Node,
	val right: Name
) : Node {
	override val base = Base()
	var intSupplier: (() -> Long)? = null
}

class ArrayNode(
	val left: Node,
	val right: Node,
	override var sym: Sym? = null
) : SymNode {
	override val base = Base()
}

class BinNode(
	val op     : BinOp,
	val left   : Node,
	val right  : Node,
) : Node {
	override val base = Base()
}

class NameNode(val value: Name, override var sym: Sym? = null) : SymNode {
	override val base = Base()
}

data class OpNode(val type: OpType, val width: Width?, val node: Node, val reg: Reg) : Node {
	override val base = Base()

	val isMem get() = type == OpType.MEM
	val isImm get() = type == OpType.IMM

	companion object {
		val NULL = OpNode(OpType.NONE, null, NullNode, Reg.NONE)
		fun reg(reg: Reg) = OpNode(reg.type, reg.type.gpWidth, NullNode, reg)
		fun mem(width: Width?, mem: Node) = OpNode(OpType.MEM, width, mem, Reg.NONE)
		fun imm(width: Width?, imm: Node) = OpNode(OpType.IMM, width, imm, Reg.NONE)
	}
}



class Ins(
	val mnemonic : Mnemonic,
	val op1      : OpNode,
	val op2      : OpNode,
	val op3      : OpNode,
	val op4      : OpNode
) : Node, PosSym, SizedSym {

	override val base = Base()
	override var size = 0

	val opCount = when {
		op1 == OpNode.NULL -> 0
		op2 == OpNode.NULL -> 1
		op3 == OpNode.NULL -> 2
		op4 == OpNode.NULL -> 3
		else               -> 4
	}

	val r1 get() = op1.reg
	val r2 get() = op2.reg
	val r3 get() = op3.reg
	val r4 get() = op4.reg

	fun high() = op1.reg.high or op2.reg.high or op3.reg.high or op4.reg.high

}



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