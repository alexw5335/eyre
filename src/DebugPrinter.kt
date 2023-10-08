package eyre



class DebugPrinter(val context: Context) {

}



private fun Appendable.appendSymbol(sym: Sym?) {
	if(sym == null) {
		append("*NULL*")
		return
	}

	append(sym.qualifiedName)
	append(" (")
	append(sym::class.simpleName)
	if(sym is TypedSym) {
		append(", type=")
		append(sym.type.qualifiedName)
	}
	append(')')
}



fun Appendable.appendNodes(node: Node) {

}
fun Appendable.appendNode(node: Node, indent: Int) {
	fun appendChild(node: Node) {
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
			appendSymbol(node.sym)
			append(')')
			appendLine()
		}

		is UnNode  -> {
			append("UnaryNode ")
			append(node.op.string)
			appendLine()
			appendChild(node.node)
		}

		is BinNode -> {
			append("BinaryNode ")
			append(node.op.string)
			appendLine()
			appendChild(node.left)
			appendChild(node.right)
		}

		else -> append("${node::class.simpleName} TODO: Implement debug output")
	}
}