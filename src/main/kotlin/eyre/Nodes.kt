package eyre



/*
Interfaces
 */



/**
 * Marker interface for AST nodes
 */
sealed interface AstNode



/**
 * Convenience interface for any node that needs to be set as the parent of a symbol.
 */
@Suppress("LeakingThis")
sealed class SymContainerNode(symbol: Symbol) : AstNode {
	init {
		symbol.node = this
	}
}



/**
 *  A node that returns a symbol when used in an expression.
 */
sealed interface SymNode : AstNode {
	val symbol: Symbol?
}



/**
 * Marker interface for nodes that can be used as operands in instruction nodes.
 */
sealed interface OpNode : AstNode



/*
Nodes
 */



class TypeNode(
	val name: Name?,
	val names: Array<Name>?,
	val arraySizes: Array<AstNode>?,
	var isInferred: Boolean = false
) : SymNode {
	override var symbol: Symbol? = null
}

class ImportNode(val names: Array<Name>) : AstNode

class ArrayNode(val receiver: SymNode, val index: AstNode) : SymNode, OpNode {
	override var symbol: Symbol? = null
}

class ScopeEndNode(val symbol: ScopedSymbol): SymContainerNode(symbol)

class NamespaceNode(val symbol: Namespace) : SymContainerNode(symbol)

class IntNode(val value: Long) : AstNode, OpNode

class FloatNode(val value: Double) : AstNode, OpNode

class RegNode(val value: GpReg) : OpNode { val width get() = value.width }

class UnaryNode(val op: UnaryOp, val node: AstNode) : AstNode, OpNode

class BinaryNode(val op: BinaryOp, val left: AstNode, val right: AstNode) : AstNode, OpNode

class StringNode(val value: String) : AstNode, OpNode, SymNode {
	override var symbol: StringLiteralSymbol? = null
}

class LabelNode(val symbol: LabelSymbol) : SymContainerNode(symbol)

class ProcNode(val symbol: ProcSymbol, val stackNodes: List<AstNode>) : SymContainerNode(symbol)

class MemNode(val width: Width?, val value: AstNode) : OpNode

class TypedefNode(val symbol: TypedefSymbol, val value: TypeNode) : SymContainerNode(symbol)

class SegRegNode(val value: SegReg) : OpNode

class StRegNode(val value: StReg) : OpNode

class SseRegNode(val value: SseReg) : OpNode

class MmxNode(val value: MmxReg) : OpNode

class ConstNode(val symbol: ConstSymbol, val value: AstNode) : SymContainerNode(symbol)

class EnumEntryNode(val symbol: EnumEntrySymbol, val value: AstNode?) : SymContainerNode(symbol)

class EnumNode(val symbol: EnumSymbol, val entries: ArrayList<EnumEntryNode>) : SymContainerNode(symbol)

class NameNode(val name: Name, override var symbol: Symbol? = null) : SymNode, OpNode

class DotNode(val left: SymNode, val right: SymNode) : SymNode by right, OpNode

class RefNode(val left: SymNode, val right: NameNode) : SymNode by right, OpNode

class MemberNode(val symbol: MemberSymbol, val type: TypeNode) : SymContainerNode(symbol)

class StructNode(val symbol: StructSymbol, val members: List<MemberNode>) : SymContainerNode(symbol)

class InsNode(
	val prefix   : Prefix?,
	val mnemonic : Mnemonic,
	val size     : Int,
	val op1      : OpNode?,
	val op2      : OpNode?,
	val op3      : OpNode?,
	val op4      : OpNode?
) : AstNode

class VarResNode(val symbol: VarResSymbol, val type: TypeNode) : SymContainerNode(symbol)

class DbPart(val width: Width, val nodes: List<AstNode>) : AstNode

class VarDbNode(val symbol: VarDbSymbol, val type: TypeNode?, val parts: List<DbPart>) : SymContainerNode(symbol)

class VarAliasNode(val symbol: VarAliasSymbol, val type: TypeNode, val value: AstNode) : SymContainerNode(symbol)

class VarInitNode(
	val symbol      : VarInitSymbol,
	val type        : TypeNode,
	val initialiser : AstNode
) : SymContainerNode(symbol)

class EqualsNode(val left: AstNode, val right: AstNode) : AstNode {
	var offset: Int = 0
}

class InitNode(val entries: List<Entry>) : AstNode {
	class Entry(val node: AstNode, var type: Type = VoidType, var offset: Int = 0)
	var type: Type? = null
}

class IndexNode(val index: AstNode) : AstNode, SymNode {
	override var symbol: Symbol? = null
}



/*
Helper functions
 */



fun UnaryNode.calculate(function: (AstNode, Boolean) -> Long, validity: Boolean) = op.calculate(
	function(node, validity && (op == UnaryOp.POS))
)



fun BinaryNode.calculate(function: (AstNode, Boolean) -> Long, validity: Boolean) = op.calculate(
	function(left, validity && (op == BinaryOp.ADD || op == BinaryOp.SUB)),
	function(right, validity && (op == BinaryOp.ADD))
)