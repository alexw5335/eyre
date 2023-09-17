package eyre




sealed class AstNode {
	var srcPos: SrcPos? = null
	var resolved = false // Used by Symbol
	var resolving = false // Used by Symbol
}



interface SymHolder {
	var symbol: Symbol?
}



interface Symbol {
	val scope: Scope
	val name: Name
	val qualifiedName get() = if(scope.isEmpty) "$name" else "$scope.$name"
}



interface Type : Symbol {
	val size: Int
	val alignment get() = size
}



interface TypedSymbol : Symbol {
	val type: Type
}



class ArrayType(val type: Type) : Type {
	override val scope = Scopes.EMPTY
	override val name = Names.EMPTY
	var count = 0
	override val size get() = type.size * count
	override val alignment = type.alignment
}



abstract class IntType(name: String, override val size: Int) : Type {
	override val scope = Scopes.EMPTY
	override val name = Names[name]
}



object ByteType : IntType("byte", 1)

object WordType : IntType("word", 2)

object DwordType : IntType("dword", 4)

object QwordType : IntType("qword", 8)

object VoidType : Type {
	override val scope = Scopes.EMPTY
	override val name = Names.EMPTY
	override val size = 0
}



interface IntSymbol : Symbol {
	val intValue: Long
}



class AnonPosSymbol(override var section: Section, override var pos: Int) : PosSymbol {
	override val name = Names.EMPTY
	override val scope = Scopes.EMPTY
}



interface ScopedSymbol : Symbol {
	val thisScope: Scope
}



interface PosSymbol : Symbol {
	var pos: Int
	var section: Section
}



/**
 * A symbol that defines an offset into some base symbol, typically another [OffsetSymbol] or a [PosSymbol].
 */
interface OffsetSymbol : Symbol {
	val offset: Int
}



data object NullNode : AstNode()

class ScopeEnd(val symbol: Symbol?): AstNode()



class DllImport(
	override val scope: Scope,
	override val name: Name
) : PosSymbol {
	override var section = Section.RDATA
	override var pos = 0
}



class Member(
	override val scope: Scope,
	override val name: Name,
	val parent: Struct,
	val typeNode: TypeNode,
) : IntSymbol, TypedSymbol, OffsetSymbol {
	var size = 0
	override var type: Type = VoidType
	override var offset = 0
	override var intValue = offset.toLong()
}



class Struct(
	override val scope: Scope,
	override val name: Name,
	override val thisScope: Scope,
	val members: List<Member>
) : AstNode(), ScopedSymbol {
	var size = 0
	var alignment = 0
}



class VarRes(
	override val scope: Scope,
	override val name: Name,
	val typeNode: TypeNode?
) : AstNode(), TypedSymbol, PosSymbol {
	override var pos = 0
	override var section = Section.BSS
	override var type: Type = VoidType
}



class EnumEntry(
	override val scope: Scope,
	override val name: Name,
	val parent: Enum,
	val valueNode: AstNode?
) : AstNode(), Symbol {
	var value = 0
}



class Const(
	override val scope: Scope,
	override val name: Name,
	val valueNode: AstNode
) : AstNode(), IntSymbol {
	override var intValue = 0L
}



class Typedef(
	override val scope: Scope,
	override val name: Name,
	val typeNode: TypeNode,
) : AstNode(), Symbol



class TypeNode(
	val name: Name?,
	val names: Array<Name>?,
	val arraySizes: Array<AstNode>?
) : AstNode() {
	var type: Type = VoidType
}



class Enum(
	override val scope: Scope,
	override val name: Name,
	override val thisScope: Scope,
	val entries: ArrayList<EnumEntry>
) : AstNode(), ScopedSymbol



class VarDb(
	override val scope: Scope,
	override val name : Name,
	val typeNode      : TypeNode?,
	val parts         : List<Part>
) : AstNode(), PosSymbol, TypedSymbol {
	class Part(val width: Width, val nodes: List<AstNode>)
	override var pos = 0
	override var section = Section.TEXT
	override var type: Type = VoidType
}



class Proc(
	override val scope     : Scope,
	override val name      : Name,
	override val thisScope : Scope,
	val parts              : List<AstNode>
): AstNode(), ScopedSymbol, PosSymbol {
	override var pos = 0
	override var section = Section.TEXT
	var size = 0 // Set by the Assembler
}

class Namespace(
	override val scope     : Scope,
	override val name      : Name,
	override val thisScope : Scope
) : AstNode(), ScopedSymbol

class Label(
	override val scope: Scope,
	override val name: Name
) : AstNode(), PosSymbol {
	override var pos = 0
	override var section = Section.TEXT
}

class RegNode(val value: Reg) : AstNode()

/** [symbol] is only for string literals in OpNodes */
class StringNode(val value: String, var symbol: Symbol? = null) : AstNode()

class FloatNode(val value: Double) : AstNode()

class IntNode(val value: Long) : AstNode()

class UnaryNode(val op: UnaryOp, val node: AstNode) : AstNode()

class BinaryNode(
	val op     : BinaryOp,
	val left   : AstNode,
	val right  : AstNode,
	var symbol : Symbol? = null
) : AstNode()

class NameNode(val value: Name, var symbol: Symbol? = null) : AstNode()

class OpNode(val type: OpType, val width: Width?, val node: AstNode, val reg: Reg) : AstNode() {
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
) : AstNode() {

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