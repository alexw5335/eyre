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

sealed interface AstNode : NodeOrSym {
	var srcPos get() = base.srcPos; set(value) { base.srcPos = value }
}

sealed interface Symbol : NodeOrSym {
	val scope get() = base.scope
	val name get() = base.name
	// If all compile-time constants that can be accessed by referencing this symbol have been calculated.
	var resolved get() = base.resolved; set(v) { base.resolved = v }
	var resolving get() = base.resolving; set(v) { base.resolving = v }
	val notResolved get() = !resolved

	val qualifiedName get() = if(scope.isEmpty) "$name" else "$scope.$name"
}

interface Type : Symbol {
	val size: Int
	val alignment get() = size
}

interface TypedSymbol : Symbol {
	val type: Type
}

interface IntSymbol : Symbol {
	val intValue: Long
}

interface ScopedSymbol : Symbol {
	val thisScope get() = base.thisScope
}

/**
 * - [pos] and [section] should be set by the Assembler
 */
interface PosSymbol : Symbol {
	var pos get() = base.pos; set(value) { base.pos = value }
	var section get() = base.section; set(value) { base.section = value }
}

/**
 * A symbol that defines an offset into some base symbol, typically another [OffsetSymbol] or a [PosSymbol].
 */
interface OffsetSymbol : Symbol {
	val offset: Int
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

class AnonPosSymbol(override var section: Section, override var pos: Int) : PosSymbol {
	override val base = Base.EMPTY
}

data object NullNode : AstNode {
	override val base = Base.EMPTY
}

class ScopeEnd(val symbol: Symbol?): AstNode {
	override val base = Base.EMPTY
}



class ImportNode(val names: Array<Name>) : AstNode {
	override val base = Base()
}



class PosRefSym(
	val receiver: PosSymbol,
	val offset: Int,
	override val type: Type
) : PosSymbol, TypedSymbol {
	override val base = Base.EMPTY
	override var pos
		set(_) = error("Cannot set ref symbol pos")
		get() = receiver.pos + offset
	override var section
		set(_) = error("Cannot set ref symbol section")
		get() = receiver.section
}



class DllImport(name: Name) : PosSymbol {
	override val base = Base().also {
		it.name = name
		it.section = Section.RDATA
	}
}



class Member(
	override val base: Base,
	val typeNode: TypeNode
) : AstNode, IntSymbol, TypedSymbol, OffsetSymbol {
	var size = 0
	override var type: Type = VoidType
	override var offset = 0
	override val intValue get() = offset.toLong()
	lateinit var parent: Struct
}



class Struct(override val base: Base, val members: List<Member>) : AstNode, ScopedSymbol, Type {
	override var size = 0
	override var alignment = 0
}



class EnumEntry(override val base: Base, val valueNode: AstNode?) : AstNode, Symbol, IntSymbol {
	var value = 0L
	override val intValue get() = value
	lateinit var parent: Enum
}



class Const(override val base: Base, val valueNode: AstNode) : AstNode, IntSymbol {
	override var intValue = 0L
}



class Typedef(
	override val base: Base,
	val typeNode: TypeNode,
	var type: Type = VoidType
) : AstNode, Type {
	override val size get() = type.size
	override val alignment get() = type.alignment
}



class TypeNode(
	val name: Name?,
	val names: Array<Name>?,
	val arraySizes: Array<AstNode>?,
) : AstNode {
	override val base = Base()
	var type: Type? = null
}



class Enum(
	override val base: Base,
	val entries: ArrayList<EnumEntry>,
	val isBitmask: Boolean
) : AstNode, ScopedSymbol, Type {
	override var size = 0
}



class Var(
	override val base : Base,
	val typeNode      : TypeNode?,
	val valueNode     : AstNode?,
	val isVal         : Boolean
) : AstNode, PosSymbol, TypedSymbol {
	override var type: Type = VoidType
	var size = 0
}



class Proc(override val base: Base, val parts: List<AstNode>): AstNode, ScopedSymbol, PosSymbol {
	var size = 0 // Set by the Assembler
	class StackInfo(val regs: List<RegNode>, val bytes: Int)
	var stackInfo: StackInfo? = null
}



class InitNode(val values: List<AstNode>) : AstNode {
	override val base = Base()
}

class IndexNode(val index: AstNode) : AstNode {
	override val base = Base()
}

class Directive(val name: Name, val values: List<AstNode>) : AstNode {
	override val base = Base()
	var target: AstNode = NullNode
}

class Namespace(override val base: Base) : AstNode, ScopedSymbol

class Label(override val base: Base) : AstNode, PosSymbol

class RegNode(val value: Reg) : AstNode {
	override val base = Base()
}

/** [symbol] is only for string literals in OpNodes */
class StringNode(val value: String, override var symbol: Symbol? = null) : AstNode, SymNode {
	override val base = Base()
}

class FloatNode(val value: Double) : AstNode {
	override val base = Base()
}

class IntNode(val value: Long) : AstNode {
	override val base = Base()
}

class UnaryNode(val op: UnaryOp, val node: AstNode) : AstNode {
	override val base = Base()
}

interface SymNode : AstNode {
	var symbol: Symbol?
}

class DotNode(
	val left: AstNode,
	val right: Name,
	override var symbol: Symbol? = null
) : SymNode {
	override val base = Base()
}

class ReflectNode(
	val left: AstNode,
	val right: Name
) : AstNode {
	override val base = Base()
	var intSupplier: (() -> Long)? = null
}

class ArrayNode(
	val left: AstNode,
	val right: AstNode,
	override var symbol: Symbol? = null
) : SymNode {
	override val base = Base()
}

class BinaryNode(
	val op     : BinaryOp,
	val left   : AstNode,
	val right  : AstNode,
) : AstNode {
	override val base = Base()
}

class NameNode(val value: Name, override var symbol: Symbol? = null) : SymNode {
	override val base = Base()
}

data class OpNode(val type: OpType, val width: Width?, val node: AstNode, val reg: Reg) : AstNode {
	override val base = Base()

	val isMem get() = type == OpType.MEM
	val isImm get() = type == OpType.IMM

	companion object {
		val NULL = OpNode(OpType.NONE, null, NullNode, Reg.NONE)
		fun reg(reg: Reg) = OpNode(reg.type, reg.type.gpWidth, NullNode, reg)
		fun mem(width: Width?, mem: AstNode) = OpNode(OpType.MEM, width, mem, Reg.NONE)
		fun imm(width: Width?, imm: AstNode) = OpNode(OpType.IMM, width, imm, Reg.NONE)
	}
}



class Ins(
	val mnemonic : Mnemonic,
	val op1      : OpNode,
	val op2      : OpNode,
	val op3      : OpNode,
	val op4      : OpNode
) : AstNode {

	override val base = Base()

	val size = when {
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



inline fun UnaryNode.calculate(validity: Boolean, function: (AstNode, Boolean) -> Long): Long = op.calculate(
	function(node, validity && (op == UnaryOp.POS))
)

inline fun UnaryNode.calculate(function: (AstNode) -> Long): Long =
	op.calculate(function(node))

inline fun BinaryNode.calculate(validity: Boolean, function: (AstNode, Boolean) -> Long): Long = op.calculate(
	function(left, validity && (op == BinaryOp.ADD || op == BinaryOp.SUB)),
	function(right, validity && (op == BinaryOp.ADD))
)

inline fun BinaryNode.calculate(function: (AstNode) -> Long): Long =
	op.calculate(function(left), function(right))