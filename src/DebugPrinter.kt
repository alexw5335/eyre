package eyre



class DebugPrinter(val context: CompilerContext) {

}



private fun Appendable.appendSymbol(symbol: Symbol?) {
	if(symbol == null) {
		append("*NULL*")
		return
	}

	append(symbol.qualifiedName)
	append(" (")
	append(symbol::class.simpleName)
	if(symbol is TypedSymbol) {
		append(", type=")
		append(symbol.type.qualifiedName)
	}
	append(')')
}



fun Appendable.appendNodes(node: AstNode) {

}
fun Appendable.appendNode(node: AstNode, indent: Int) {
	fun appendChild(node: AstNode) {
		repeat(indent + 1) { append('\t') }
		appendNode(node, indent + 1)
	}

	when(node) {
		is IntNode    -> appendLine(node.value.toString())
		is FloatNode  -> appendLine(node.value.toString())
		is RegNode    -> appendLine(node.value.string)

		is StringNode -> {
			append('\"')
			append(node.value.replace("\n", "\\n"))
			append('"')
			appendLine()
		}

		is Label -> {
			append("LABEL ")
			append(node.qualifiedName)
			appendLine()
		}

		is Namespace -> {
			append("NAMESPACE ")
			append(node.thisScope.toString())
			appendLine()
		}

		is NameNode -> {
			append(node.value.string)
			append(" (symbol = ")
			appendSymbol(node.symbol)
			append(')')
			appendLine()
		}

		is UnaryNode  -> {
			append("UnaryNode ")
			append(node.op.symbol)
			appendLine()
			appendChild(node.node)
		}

		is BinaryNode -> {
			append("BinaryNode ")
			append(node.op.string)
			appendLine()
			appendChild(node.left)
			appendChild(node.right)
		}

		else -> append("${node::class.simpleName} TODO: Implement debug output")
	}
}