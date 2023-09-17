package eyre


// Type system is a mess


class Base {
	var srcPos: SrcPos? = null
	var scope = Scopes.EMPTY
	var name = Names.EMPTY
	var thisScope = Scopes.EMPTY
	var resolved = false
	var resolving = false
	var pos = 0
	var section = Section.TEXT


	companion object {
		val EMPTY = Base().also { it.resolved = true }
	}
}

interface NodeOrSym {
	val base: Base
}

interface AstNode : NodeOrSym {
	var srcPos get() = base.srcPos; set(value) { base.srcPos = value }
}

abstract class SimpleNode : AstNode {
	override val base = Base()
}

interface Symbol : NodeOrSym {
	val scope get() = base.scope
	val name get() = base.name
	var resolved get() = base.resolved; set(v) { base.resolved = v }
	var resolving get() = base.resolving; set(v) { base.resolving = v }

	val qualifiedName get() = if(scope.isEmpty) "$name" else "$scope.$name"
}

interface NodeAndSym : AstNode, Symbol

interface SymNode : AstNode {
	var symbol: Symbol?
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

abstract class IntType(name: String, override val size: Int) : Type {
	override val base = Base().also { it.name = Names[name] }
}

object ByteType : IntType("byte", 1)

object WordType : IntType("word", 2)

object DwordType : IntType("dword", 4)

object QwordType : IntType("qword", 8)

object VoidType : Type {
	override val base = Base.EMPTY
	override val size = 0
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



class DllImport(name: Name) : PosSymbol {
	override val base = Base().also {
		it.name = name
		it.section = Section.RDATA
	}
}



class Member(override val base: Base, val typeNode: TypeNode) : AstNode, IntSymbol, TypedSymbol, OffsetSymbol {
	var size = 0
	override var type: Type = VoidType
	override var offset = 0
	override var intValue = offset.toLong()
	lateinit var parent: Struct
}



class Struct(override val base: Base, val members: List<Member>) : AstNode, ScopedSymbol {
	var size = 0
	var alignment = 0
}



class VarRes(override val base: Base, val typeNode: TypeNode?) : AstNode, TypedSymbol, PosSymbol {
	override var type: Type = VoidType
}



class EnumEntry(override val base: Base, val valueNode: AstNode?) : AstNode, Symbol {
	var value = 0
	lateinit var parent: Enum
}



class Const(override val base: Base, val valueNode: AstNode) : AstNode, IntSymbol {
	override var intValue = 0L
}



class Typedef(override val base: Base, val typeNode: TypeNode) : AstNode, Symbol



class TypeNode(
	val name: Name?,
	val names: Array<Name>?,
	val arraySizes: Array<AstNode>?
) : AstNode {
	override val base = Base()
	var type: Type = VoidType
}



class Enum(override val base: Base, val entries: ArrayList<EnumEntry>) : AstNode, ScopedSymbol



class VarDb(
	override val base : Base,
	val typeNode      : TypeNode?,
	val parts         : List<Part>
) : AstNode, PosSymbol, TypedSymbol {
	override var type: Type = VoidType
	class Part(val width: Width, val nodes: List<AstNode>)
}



class Proc(override val base: Base, val parts: List<AstNode>): AstNode, ScopedSymbol, PosSymbol {
	var size = 0 // Set by the Assembler
}



class Namespace(override val base: Base) : AstNode, ScopedSymbol

class Label(override val base: Base) : AstNode, PosSymbol

class RegNode(val value: Reg) : SimpleNode()

/** [symbol] is only for string literals in OpNodes */
class StringNode(val value: String, var symbol: Symbol? = null) : SimpleNode()

class FloatNode(val value: Double) : SimpleNode()

class IntNode(val value: Long) : SimpleNode()

class UnaryNode(val op: UnaryOp, val node: AstNode) : SimpleNode()

/** [symbol] is only for `.` and `::` operations */
class BinaryNode(
	val op     : BinaryOp,
	val left   : AstNode,
	val right  : AstNode,
	var symbol : Symbol? = null
) : SimpleNode()

class NameNode(val value: Name, var symbol: Symbol? = null) : SimpleNode()

class OpNode(val type: OpType, val width: Width?, val node: AstNode, val reg: Reg) : SimpleNode() {
	val isMem get() = type == OpType.MEM
	val isImm get() = type == OpType.IMM

	companion object {
		val NULL = OpNode(OpType.NONE, null, NullNode, Reg.NONE)
		fun reg(reg: Reg) = OpNode(reg.type, reg.type.gpWidth, NullNode, reg)
		fun mem(width: Width?, mem: AstNode) = OpNode(OpType.MEM, width, mem, Reg.NONE)
		fun imm(width: Width?, imm: AstNode) = OpNode(OpType.IMM, width, imm, Reg.NONE)
	}
}



class InsNode(
	val mnemonic : Mnemonic,
	val op1      : OpNode,
	val op2      : OpNode,
	val op3      : OpNode,
	val op4      : OpNode
) : SimpleNode() {

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



inline fun BinaryNode.calculate(validity: Boolean, function: (AstNode, Boolean) -> Long): Long = op.calculate(
	function(left, validity && (op == BinaryOp.ADD || op == BinaryOp.SUB)),
	function(right, validity && (op == BinaryOp.ADD))
)