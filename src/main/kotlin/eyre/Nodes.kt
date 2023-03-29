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
): AstNode {
	init { symbol.node = this }
}

class NamespaceNode(
	override val srcPos: SrcPos,
	val symbol: Namespace
) : AstNode {
	init { symbol.node = this }
}

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
	val value: Name
) : AstNode, OpNode

class LabelNode(
	override val srcPos: SrcPos,
	val symbol: LabelSymbol
) : AstNode {
	init { symbol.node = this }
}

class ProcNode(
	override val srcPos: SrcPos,
	val symbol: ProcSymbol
) : AstNode {
	init { symbol.node = this }
}

class MemNode(
	override val srcPos: SrcPos,
	val width: Width?,
	val value: AstNode
) : OpNode

class DbPart(
	override val srcPos: SrcPos,
	val width: Width,
	val nodes: List<AstNode>
) : AstNode

class DbNode(
	override val srcPos: SrcPos,
	val symbol: DbVarSymbol,
	val parts: List<DbPart>
) : AstNode {
	init { symbol.node = this }
}

class MemberInitNode(
	override val srcPos: SrcPos,
	val member: Name?,
	val value: AstNode
) : AstNode

class VarNode(
	override val srcPos: SrcPos,
	val symbol: VarSymbol,
	val value: AstNode?,
	val type: SymProviderNode,
	val inits: List<MemberInitNode>
) : AstNode {
	init { symbol.node = this }
}

class TypedefNode(
	override val srcPos: SrcPos,
	val symbol: TypedefSymbol,
	val value: AstNode
) : AstNode {
	init { symbol.node = this }
}

class ResNode(
	override val srcPos: SrcPos,
	val symbol: ResVarSymbol,
	val size: AstNode
) : AstNode {
	init { symbol.node = this }
}

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
) : AstNode {
	init { symbol.node = this }
}

class EnumEntryNode(
	override val srcPos: SrcPos,
	val symbol: EnumEntrySymbol,
	val value: AstNode?
) : AstNode {
	init { symbol.node = this }
}

class EnumNode(
	override val srcPos: SrcPos,
	val symbol: EnumSymbol,
	val entries: ArrayList<EnumEntryNode>
) : AstNode {
	init { symbol.node = this }
}

class BlockNode(
	override val srcPos: SrcPos,
	val receiver: SymProviderNode
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
	val name: Name,
	override var symbol: Symbol? = null
) : SymProviderNode, OpNode

class SymDotNode(
	override val srcPos: SrcPos,
	val names: NameArray,
	override var symbol: Symbol? = null
) : SymProviderNode, OpNode

class DebugLabelNode(
	override val srcPos: SrcPos,
	val symbol: DebugLabelSymbol
) : AstNode {
	init { symbol.node = this }
}

class MemberNode(
	override val srcPos: SrcPos,
	val symbol: MemberSymbol,
	val type: AstNode
) : AstNode {
	init { symbol.node = this }
}

class StructNode(
	override val srcPos: SrcPos,
	val symbol: StructSymbol,
	val members: List<MemberNode>
) : AstNode {
	init { symbol.node = this }
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



/*
Formatting
 */



/*fun AstNode.getChildren(): List<AstNode> = when(this) {
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
	is VarDefNode        -> parts
	is VarPart        -> nodes
	is ResNode        -> listOf(size)
	is RefNode        -> listOf(left, right)
	is ConstNode      -> listOf(value)
	is EnumNode       -> entries
	is EnumEntryNode  -> listOfNotNull(value)
	is StructNode     -> members
	is MemberNode     -> emptyList()
}*/



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
		if(symbol.manual) {
			for(member in members) {
				append('\t')
				append(member.printString)
				append('\n')
			}
			append('\t')
			append(symbol.size)
			append("\n}")
		} else {
			for(member in members) {
				append('\t')
				append(member.type.printString)
				append(' ')
				append(member.symbol.name)
				append('\n')
			}
			append('}')
		}
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

	is DbPart -> "${width.varString} ${nodes.joinToString { it.printString }}"
	is DbNode -> "var ${symbol.name} ${parts.joinToString { it.printString }}"
	is ResNode -> "var ${symbol.name} res ${size.printString}"
	is RefNode -> "${left.printString}::${right.printString}"
	is ConstNode -> "const ${symbol.name} = ${value.printString}"

	is VarNode -> buildString {
		append("var ")
		append(symbol.name)
		append(": ")
		append(type.printString)
		if(value != null) {
			append(" = ")
			append(value.printString)
		}
		if(inits.isNotEmpty()) {
			append(" { ")
			for(i in inits) {
				append(i.member)
				append(" = ")
				append(i.value.printString)
				append(", ")
			}
			append("} ")
		}
	}

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
