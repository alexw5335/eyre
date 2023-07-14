@file:Suppress("RecursivePropertyAccessor")

package eyre



val InsNode.nasmString: String get() = printString.replace("xword", "oword")

val AstNode.printString: String get() = when(this) {
	is LabelNode      -> "label ${symbol.name}"
	is StringNode     -> "\"$value\""
	is IntNode        -> value.toString()
	is UnaryNode      -> "${op.symbol}${node.printString}"
	is BinaryNode     -> "(${left.printString} ${op.symbol} ${right.printString})"
	is DotNode        -> "(${left.printString}.${right.printString})"
	is RegNode        -> value.string
	is NameNode       -> name.string
	is NamespaceNode  -> "namespace ${symbol.name}"
	is ScopeEndNode   -> "scope end"
	is VarResNode     -> "var ${symbol.name}: ${type.printString}"
	is ArrayNode      -> "${receiver.printString}[${index.printString}]"
	is ConstNode      -> "const ${symbol.name} = ${value.printString}"
	is TypedefNode    -> "typedef ${symbol.name} = ${value.printString}"
	is FloatNode      -> "$value"
	is RefNode        -> "${left.printString}::${right.printString}"
	is EqualsNode     -> "${left.printString} = ${right.printString}"

	is OpNode -> buildString {
		when(type) {
			OpNodeType.MEM -> { if(width != null) append("${width.string} "); append("[${node.printString}]") }
			OpNodeType.REG -> append(reg.string)
			OpNodeType.IMM -> { if(width != null) append("${width.string} "); append(node.printString) }
		}
	}

	is TypeNode -> buildString {
		name?.let(::append)
		names?.joinTo(this, ".")
		if(arraySizes != null) {
			for(size in arraySizes) {
				append('[')
				append(size.printString)
				append(']')
			}
		}
	}

	is StructNode -> buildString {
		append("struct ")
		append(symbol.name)
		append(" {\n")
		for(m in members) {
			append('\t')
			append(m.type.printString)
			append(' ')
			append(m.symbol.name)
			append('\n')
		}
		append("}")
	}

	is VarDbNode -> buildString {
		append("var ")
		append(symbol.name)
		if(type != null) {
			append(": ")
			append(type.printString)
		}
		for(part in parts) {
			append("\n\t")
			append(part.width.varString)
			append(' ')
			for((i, n) in part.nodes.withIndex()) {
				append(n.printString)
				if(i < part.nodes.size - 1)
					append(", ")
			}
		}
	}

	is VarInitNode -> buildString {
		append("var ")
		append(symbol.name)
		append(": ")
		append(type.printString)
		append(" = ")
		append(initialiser.printString)
	}

	is InitNode -> buildString {
		append("{ ")
		for(node in entries) {
			append(node.node.printString)
			append(", ")
		}
		append("}")
	}

	is IndexNode -> "[${index.printString}]"

	is ProcNode -> buildString {
		append("proc ")
		append(symbol.name)
	}

	is InsNode -> buildString {
		append(mnemonic.string)
		if(op1 == null) return@buildString
		append(" ${op1.printString}")
		if(op2 == null) return@buildString
		append(", ${op2.printString}")
		if(op3 == null) return@buildString
		append(", ${op3.printString}")
		if(op4 == null) return@buildString
		append(", ${op4.printString}")
	}

	else -> toString()
}



val Token.printString get(): String = when(this) {
	is IntToken    -> "$value"
	is FloatToken  -> "$value"
	is StringToken -> value
	is Name        -> string
	else           -> ""
}



val Type.printString get(): String = when(this) {
	is ArraySymbol -> "${type.printString}[$count]"
	else -> qualifiedName
}