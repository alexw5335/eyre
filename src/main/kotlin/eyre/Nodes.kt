@file:Suppress("LeakingThis", "RecursivePropertyAccessor")

package eyre



/*
Interfaces
 */



sealed interface AstNode



/** Convenience interface for any node that needs to be set as the parent of a symbol. */
sealed class SymContainerNode(symbol: Symbol) : AstNode {
	init { symbol.node = this }
}



/** A node that represents a symbol when used in an expression. */
sealed interface SymNode : AstNode {
	val symbol: Symbol?
}



sealed interface OpNode : AstNode



class ImportNode(val import: SymNode) : AstNode

class ArrayNode(val receiver: AstNode, val index: AstNode?) : SymNode {
	override var symbol: Symbol? = null
}

class ScopeEndNode(val symbol: ScopedSymbol): SymContainerNode(symbol)

class NamespaceNode(val symbol: Namespace) : SymContainerNode(symbol)

class IntNode(val value: Long) : AstNode, OpNode

class RegNode(val value: Register) : OpNode { val width get() = value.width }

class UnaryNode(val op: UnaryOp, val node: AstNode) : AstNode, OpNode

class BinaryNode(val op: BinaryOp, val left: AstNode, val right: AstNode) : AstNode, OpNode

class StringNode(val value: Name) : AstNode, OpNode

class LabelNode(val symbol: LabelSymbol) : SymContainerNode(symbol)

class ProcNode(val symbol: ProcSymbol) : SymContainerNode(symbol)

class MemNode(val width: Width?, val value: AstNode) : OpNode

class TypedefNode(val symbol: TypedefSymbol, val value: AstNode) : SymContainerNode(symbol)

class SegRegNode(val value: SegReg) : OpNode

class FpuRegNode(val value: FpuReg) : OpNode

class ConstNode(val symbol: ConstSymbol, val value: AstNode) : SymContainerNode(symbol)

class EnumEntryNode(val symbol: EnumEntrySymbol, val value: AstNode?) : SymContainerNode(symbol)

class EnumNode(val symbol: EnumSymbol, val entries: ArrayList<EnumEntryNode>) : SymContainerNode(symbol)

class NameNode(val name: Name, override var symbol: Symbol? = null) : SymNode, OpNode

class NamesNode(val names: Array<Name>, override var symbol: Symbol? = null) : SymNode, OpNode

class DotNode(val left: AstNode, val right: SymNode) : SymNode by right, OpNode

class RefNode(val left: SymNode, val right: SymNode) : SymNode by right, OpNode

class MemberNode(val symbol: MemberSymbol, val type: AstNode) : SymContainerNode(symbol)

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

class VarResNode(val symbol: VarResSymbol, val type: AstNode) : SymContainerNode(symbol)



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



/*
Formatting
 */



val AstNode.printString: String get() = when(this) {
	is LabelNode      -> "label ${symbol.name}"
	is StringNode     -> "\"${value.string}\""
	is IntNode        -> value.toString()
	is UnaryNode      -> "${op.symbol}${node.printString}"
	is BinaryNode     -> "(${left.printString} ${op.symbol} ${right.printString})"
	is DotNode        -> "(${left.printString}.${right.printString})"
	is RegNode        -> value.string
	is NameNode       -> name.string
	is NamesNode      -> names.joinToString(".")
	is NamespaceNode  -> "namespace ${symbol.name}"
	is ScopeEndNode   -> "scope end"
	is MemNode        -> if(width != null) "${width.string} [${value.printString}]" else "[${value.printString}]"
	is SegRegNode     -> value.name.lowercase()
	is FpuRegNode     -> value.string
	is VarResNode     -> "var ${symbol.name}: ${type.printString}"
	is ArrayNode      -> "${receiver.printString}[${index?.printString ?: ""}]"

	is ConstNode -> "const ${symbol.name} = ${value.printString}"
	is TypedefNode -> "typedef ${symbol.name} = ${value.printString}"


	else -> toString()
}