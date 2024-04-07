package eyre



private val String.printable: String get() = replace("\n", "\\n")

val Node.exprString get() = StringBuilder().also { it.appendExpr(this) }.toString()



fun printFullExpr(node: Node, indent: Int = 0) {
	for(i in 0 ..< indent) print("    ")
	when(node) {
		is BinNode -> {
			if(node.isLeaf)
				println("${node.op}  \u001B[32mLEAF\u001B[0m")
			else
				println("${node.op}  \u001B[31m${node.numRegs}\u001B[0m")
			printFullExpr(node.left, indent + 1)
			printFullExpr(node.right, indent + 1)
		}
		is IntNode -> println(node.value)
		is UnNode -> {
			println("${node.op}  \u001B[31m${node.numRegs}\u001B[0m")
			printFullExpr(node.child, indent + 1)
		}
		is NameNode -> println(node.name)
		else -> error("Unhandled node: $node")
	}
}



val Operand.printString: String get() = when(this) {
	is ImmOperand -> value.toString()
	is RegOperand -> reg.toString()
	is RelocOperand -> "[RIP]"
	is StackOperand -> if(disp < 0) "[RBP - ${-disp}]" else "[RBP + $disp]"
}



val Instruction.printString get() = buildString {
	append(mnemonic)
	for(i in 0 ..< 5 - mnemonic.name.length) append(' ')
	if(op1 == null) return@buildString
	append(" ")
	append(op1.printString)
	if(op2 == null) return@buildString
	append(", ")
	append(op2.printString)
	if(op3 == null) return@buildString
	append(", ")
	append(op3.printString)
}



private fun StringBuilder.appendExpr(node: Node) { when(node) {
	is IntNode -> append(node.value.toString())
	is StringNode -> append("\"${node.value.printable}\"")
	is NameNode -> append(node.name.string)

	is TypeNode -> {
		append(node.names.joinToString(separator = "."))
		for(mod in node.mods) when(mod) {
			is TypeNode.PointerMod -> append("*")
			is TypeNode.ArrayMod -> {
				append('[')
				mod.sizeNode?.let { appendExpr(it) }
				append(']')
			}
		}
	}

	is RefNode -> {
		appendExpr(node.left)
		append("::")
		appendExpr(node.right)
	}

	is DotNode -> {
		append('(')
		appendExpr(node.left)
		append('.')
		appendExpr(node.right)
		append(')')
	}

	is ArrayNode -> {
		append('(')
		appendExpr(node.left)
		append('[')
		appendExpr(node.right)
		append(']')
		append(')')
	}

	is BinNode -> {
		append('(')
		appendExpr(node.left)
		append(' ')
		append(node.op.string)
		append(' ')
		appendExpr(node.right)
		append(')')
	}

	is UnNode -> {
		append('(')
		if(!node.op.isPostfix) append(node.op.string)
		appendExpr(node.child)
		if(node.op.isPostfix) append(node.op.string)
		append(')')
	}

	is CallNode -> {
		append('(')
		appendExpr(node.left)
		append('(')
		for(i in 0 ..< node.args.size - 1) {
			appendExpr(node.args[i])
			append(", ")
		}
		if(node.args.isNotEmpty())
			appendExpr(node.args[0])
		append(')')
		append(')')
	}

	else -> error("Non-printable expression node: $node")
}}
