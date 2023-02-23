package eyre



sealed interface AstNode

interface SymProviderNode : AstNode {
	val symbol: Symbol?
}



object ScopeEndNode : AstNode

class NamespaceNode(val symbol: Namespace) : AstNode

class IntNode(val value: Long) : AstNode

class RegNode(val value: Register) : AstNode { val width get() = value.width }

class UnaryNode(val op: UnaryOp, val node: AstNode) : AstNode

class BinaryNode(val op: BinaryOp, val left: AstNode, val right: AstNode) : AstNode

class StringNode(val value: StringIntern) : AstNode

class SymNode(val name: StringIntern, override var symbol: Symbol? = null) : SymProviderNode

class DotNode(val left: AstNode, val right: SymNode) : SymProviderNode { override val symbol get() = right.symbol }

class LabelNode(val symbol: LabelSymbol) : AstNode

class MemNode(val width: Width?, val value: AstNode) : AstNode

class VarPart(val width: Width, val nodes: List<AstNode>)

class VarNode(val symbol: VarSymbol, val parts: List<VarPart>) : AstNode

class ResNode(val symbol: ResSymbol, val size: AstNode) : AstNode

class InsNode(
	val mnemonic : Mnemonic,
	val size     : Int,
	val op1      : AstNode?,
	val op2      : AstNode?,
	val op3      : AstNode?,
	val op4      : AstNode?
) : AstNode



/*
Formatting
 */



@Suppress("REDUNDANT_ELSE_IN_WHEN")
val AstNode.printString: String get() = when(this) {
	is LabelNode     -> "label ${symbol.name}:"
	is StringNode    -> "\"${value.string}\""
	is IntNode       -> value.toString()
	is UnaryNode     -> "${op.symbol}${node.printString}"
	is BinaryNode    -> "(${left.printString} ${op.symbol} ${right.printString})"
	is DotNode       -> "(${left.printString}.${right.printString})"
	is RegNode       -> value.string
	is SymNode       -> "$name"
	is NamespaceNode -> "namespace ${symbol.name}"
	is ScopeEndNode  -> "scope end"
	is MemNode       -> if(width != null) "${width.string} [${value.printString}]" else "[${value.printString}]"

	is InsNode -> buildString {
		append(mnemonic.string)
		if(op1 == null) return@buildString
		append(' ')
		append(op1.printString)
		if(op2 == null) return@buildString
		append(", ")
		append(op2.printString)
		if(op3 == null) return@buildString
		append(", ")
		append(op3.printString)
		if(op4 == null) return@buildString
		append(", ")
		append(op4.printString)
	}

	is VarNode -> "var ${symbol.name} ${parts.joinToString { "${it.width.varString} ${it.nodes.joinToString { it2 -> it2.printString }}" }}"
	is ResNode -> "var ${symbol.name} res ${size.printString}"

	else             -> toString()
}
