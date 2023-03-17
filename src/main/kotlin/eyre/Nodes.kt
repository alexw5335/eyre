package eyre



sealed interface AstNode {
	val srcPos: SrcPos
}

sealed interface OpNode : AstNode



sealed interface SymProviderNode : AstNode {
	val symbol: Symbol?
}



class ImportNode(
	override val srcPos: SrcPos,
	val import: SymProviderNode
) : AstNode

class ScopeEndNode(
	override val srcPos: SrcPos,
	val symbol: ScopedSymbol
): AstNode

class NamespaceNode(
	override val srcPos: SrcPos,
	val symbol: Namespace
) : AstNode

class IntNode(
	override val srcPos: SrcPos,
	val value: Long
) : AstNode, OpNode

class RegNode(
	override val srcPos: SrcPos,
	val value: Register
) : OpNode {
	val width get() = value.width
}

class UnaryNode(
	override val srcPos: SrcPos,
	val op: UnaryOp,
	val node: AstNode
) : AstNode, OpNode

class BinaryNode(
	override val srcPos: SrcPos,
	val op: BinaryOp,
	val left: AstNode,
	val right: AstNode
) : AstNode, OpNode

class StringNode(
	override val srcPos: SrcPos,
	val value: StringIntern
) : AstNode, OpNode

class LabelNode(
	override val srcPos: SrcPos,
	val symbol: LabelSymbol
) : AstNode

class ProcNode(
	override val srcPos: SrcPos,
	val symbol: ProcSymbol
) : AstNode

class MemNode(
	override val srcPos: SrcPos,
	val width: Width?,
	val value: AstNode
) : OpNode

class VarPart(
	override val srcPos: SrcPos,
	val width: Width,
	val nodes: List<AstNode>
) : AstNode

class VarNode(
	override val srcPos: SrcPos,
	val symbol: VarSymbol,
	val parts: List<VarPart>
) : AstNode

class ResNode(
	override val srcPos: SrcPos,
	val symbol: ResSymbol,
	val size: AstNode
) : AstNode

class SegRegNode(
	override val srcPos: SrcPos,
	val value: SegReg
) : OpNode

class FpuRegNode(
	override val srcPos: SrcPos,
	val value: FpuReg
) : OpNode

class ConstNode(
	override val srcPos: SrcPos,
	val symbol: ConstSymbol,
	val value: AstNode
) : AstNode

class EnumEntryNode(
	override val srcPos: SrcPos,
	val symbol: EnumEntrySymbol,
	val value: AstNode?
) : AstNode

class EnumNode(
	override val srcPos: SrcPos,
	val symbol: EnumSymbol,
	val entries: ArrayList<EnumEntryNode>
) : AstNode

class InsNode(
	override val srcPos: SrcPos,
	val prefix   : Prefix?,
	val mnemonic : Mnemonic,
	val size     : Int,
	val op1      : OpNode?,
	val op2      : OpNode?,
	val op3      : OpNode?,
	val op4      : OpNode?
) : AstNode

class DotNode(
	override val srcPos: SrcPos,
	val left: AstNode,
	val right: SymNode
) : SymProviderNode, OpNode {
	override val symbol get() = right.symbol
}

class RefNode(
	override val srcPos: SrcPos,
	val left: SymProviderNode,
	val right: SymNode
) : SymProviderNode, OpNode {
	override val symbol get() = right.symbol
}

class SymNode(
	override val srcPos: SrcPos,
	val name: StringIntern,
	override var symbol: Symbol? = null
) : SymProviderNode, OpNode

class DebugLabelNode(
	override val srcPos: SrcPos,
	val symbol: DebugLabelSymbol
) : AstNode

class MemberNode(
	override val srcPos: SrcPos,
	val symbol: MemberSymbol
) : AstNode

class StructNode(
	override val srcPos: SrcPos,
	val symbol: StructSymbol,
	val members: List<MemberNode>
) : AstNode



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



fun AstNode.getChildren(): List<AstNode> = when(this) {
	is LabelNode,
	is StringNode,
	is IntNode,
	is RegNode,
	is FpuRegNode,
	is SymNode,
	is NamespaceNode,
	is ScopeEndNode,
	is ProcNode,
	is SegRegNode,
	is DebugLabelNode -> emptyList()
	is ImportNode     -> listOf(import)
	is UnaryNode      -> listOf(node)
	is BinaryNode     -> listOf(left, right)
	is MemNode        -> listOf(value)
	is DotNode        -> listOf(left, right)
	is InsNode        -> listOfNotNull(op1, op2, op3, op4)
	is VarNode        -> parts
	is VarPart        -> nodes
	is ResNode        -> listOf(size)
	is RefNode        -> listOf(left, right)
	is ConstNode      -> listOf(value)
	is EnumNode       -> entries
	is EnumEntryNode  -> listOfNotNull(value)
	is StructNode     -> members
	is MemberNode     -> emptyList()
}



val AstNode.printString: String get() = when(this) {
	is LabelNode      -> "label ${symbol.name}"
	is StringNode     -> "\"${value.string}\""
	is IntNode        -> value.toString()
	is UnaryNode      -> "${op.symbol}${node.printString}"
	is BinaryNode     -> "(${left.printString} ${op.symbol} ${right.printString})"
	is DotNode        -> "(${left.printString}.${right.printString})"
	is RegNode        -> value.string
	is SymNode        -> "$name"
	is NamespaceNode  -> "namespace ${symbol.name}"
	is ScopeEndNode   -> "scope end"
	is MemNode        -> if(width != null) "${width.string} [${value.printString}]" else "[${value.printString}]"
	is SegRegNode     -> value.name.lowercase()
	is FpuRegNode     -> value.string
	is DebugLabelNode -> "#debug \"${symbol.name}\""
	is ProcNode       -> "proc ${symbol.name}"

	is MemberNode     -> "${symbol.offset}  ${symbol.size}  ${symbol.name}"

	is StructNode -> buildString {
		append("struct ")
		append(symbol.name)
		append(" {")
		append('\n')
		for(member in members) {
			append('\t')
			append(member.printString)
			append('\n')
		}
		append('\t')
		append(symbol.size)
		append("\n}")
	}

	is InsNode -> buildString {
		if(prefix != null) append("${prefix.string} ")
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

	is VarPart -> "${width.varString} ${nodes.joinToString { it.printString }}"
	is VarNode -> "var ${symbol.name} ${parts.joinToString { it.printString }}"
	is ResNode -> "var ${symbol.name} res ${size.printString}"
	is RefNode -> "${left.printString}::${right.printString}"
	is ConstNode -> "const ${symbol.name} = ${value.printString}"

	is EnumEntryNode -> buildString {
		append(symbol.name)
		append(" = ")
		if(value != null)
			append(value.printString)
		else
			append(symbol.intValue)
	}

	is EnumNode -> buildString {
		appendLine("enum ${symbol.name} {")
		for(e in entries) {
			append('\t')
			append(e.printString)
			appendLine()
		}
		append('}')
	}

	else -> toString()
}
