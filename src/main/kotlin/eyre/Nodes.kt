package eyre



/*
Interfaces
 */



/**
 * Super class for AST nodes
 */
sealed class AstNode {
	var srcPos: SrcPos? = null
}



/**
 * Convenience super class for any node that needs to be set as the parent of a symbol.
 */
@Suppress("LeakingThis")
sealed class SymContainerNode(symbol: Symbol) : AstNode() {
	init {
		symbol.node = this
	}
}



/**
 *  A node that returns a symbol when used in an expression.
 */
sealed class SymNode : AstNode() {
	abstract val symbol: Symbol?
}



class OpNode private constructor(
	val type  : OpType,
	val width : Width?,
	val node  : AstNode,
	val reg   : Reg,
) : AstNode() {

	val isMem get() = type == OpType.MEM
	val isImm get() = type == OpType.IMM

	companion object {
		val NULL = OpNode(OpType.NONE, null, NullNode, Reg.NONE)
		fun reg(reg: Reg) = OpNode(reg.type, reg.type.gpWidth, NullNode, reg)
		fun mem(width: Width?, mem: AstNode) = OpNode(OpType.MEM, width, mem, Reg.NONE)
		fun imm(width: Width?, imm: AstNode) = OpNode(OpType.IMM, width, imm, Reg.NONE)
	}

}



class RegNode(val value: Reg) : AstNode()



data object NullNode : AstNode()



/*
Nodes
 */



class IntNode(val value: Long) : AstNode()

class FloatNode(val value: Double) : AstNode()

class StringNode(val value: String) : SymNode() {
	override var symbol: StringLiteralSymbol? = null
}

class ImportNode(val names: Array<Name>) : AstNode()

class PrefixNode(val prefix: InsPrefix) : AstNode()

class UnaryNode(val op: UnaryOp, val node: AstNode) : AstNode()

class BinaryNode(val op: BinaryOp, val left: AstNode, val right: AstNode) : AstNode()

class TypeNode(
	val name: Name?,
	val names: Array<Name>?,
	val arraySizes: Array<AstNode>?
) : SymNode() {
	override var symbol: Symbol? = null
}

class ArrayNode(val receiver: SymNode, val index: AstNode) : SymNode() {
	override var symbol: Symbol? = null
}

class ScopeEndNode(val symbol: ScopedSymbol): SymContainerNode(symbol)

class NamespaceNode(val symbol: Namespace) : SymContainerNode(symbol)

class DirectiveNode(val name: Name, val value: AstNode?) : AstNode()

class LabelNode(val symbol: LabelSymbol) : SymContainerNode(symbol)

class ProcNode(val symbol: ProcSymbol, val stackNodes: List<AstNode>) : SymContainerNode(symbol)

class TypedefNode(val symbol: TypedefSymbol, val value: TypeNode) : SymContainerNode(symbol)

class ConstNode(val symbol: ConstSymbol, val value: AstNode) : SymContainerNode(symbol)

class EnumEntryNode(val symbol: EnumEntrySymbol, val value: AstNode?) : SymContainerNode(symbol)

class EnumNode(val symbol: EnumSymbol, val entries: ArrayList<EnumEntryNode>) : SymContainerNode(symbol)

class NameNode(val name: Name, override var symbol: Symbol? = null) : SymNode()

class DotNode(val left: SymNode, val right: SymNode) : SymNode() {
	override val symbol get() = right.symbol
}

class RefNode(val left: SymNode, val right: NameNode) : SymNode() {
	override val symbol get() = right.symbol
}

class MemberNode(val symbol: MemberSymbol, val type: TypeNode) : SymContainerNode(symbol)

class StructNode(val symbol: StructSymbol, val members: List<MemberNode>) : SymContainerNode(symbol)

class VarResNode(val symbol: VarResSymbol, val type: TypeNode) : SymContainerNode(symbol)

class DbPart(val width: Width, val nodes: List<AstNode>) : AstNode()

class VarDbNode(val symbol: VarDbSymbol, val type: TypeNode?, val parts: List<DbPart>) : SymContainerNode(symbol)

class VarAliasNode(val symbol: VarAliasSymbol, val type: TypeNode, val value: AstNode) : SymContainerNode(symbol)

class VarInitNode(
	val symbol      : VarInitSymbol,
	val type        : TypeNode,
	val initialiser : AstNode
) : SymContainerNode(symbol)

class EqualsNode(val left: AstNode, val right: AstNode) : AstNode() {
	var offset: Int = 0
}

class InitNode(val entries: List<Entry>) : AstNode() {
	class Entry(val node: AstNode, var type: Type = VoidType, var offset: Int = 0)
	var type: Type? = null
}

class IndexNode(val index: AstNode) : SymNode() {
	override var symbol: Symbol? = null
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



/*
Instruction node
 */



class InsNode(
	val mnemonic : Mnemonic,
	val size     : Int,
	val op1      : OpNode,
	val op2      : OpNode,
	val op3      : OpNode,
	val op4      : OpNode
) : AstNode() {

	val r1 get() = op1.reg
	val r2 get() = op2.reg
	val r3 get() = op3.reg
	val r4 get() = op4.reg

	fun high() = op1.reg.high or op2.reg.high or op3.reg.high or op4.reg.high

	constructor(m: Mnemonic) :
		this(m, 0, OpNode.NULL, OpNode.NULL, OpNode.NULL, OpNode.NULL)

	constructor(m: Mnemonic, op1: OpNode) :
		this(m, 1, op1, OpNode.NULL, OpNode.NULL, OpNode.NULL)

	constructor(m: Mnemonic, op1: OpNode, op2: OpNode) :
		this(m, 2, op1, op2, OpNode.NULL, OpNode.NULL)

	constructor(m: Mnemonic, op1: OpNode, op2: OpNode, op3: OpNode) :
		this(m, 3, op1, op2, op3, OpNode.NULL)

	constructor(m: Mnemonic, op1: OpNode, op2: OpNode, op3: OpNode, op4: OpNode) :
		this(m, 4, op1, op2, op3, op4)

}