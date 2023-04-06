@file:Suppress("RecursivePropertyAccessor")

package eyre



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
	is ConstNode      -> "const ${symbol.name} = ${value.printString}"
	is TypedefNode    -> "typedef ${symbol.name} = ${value.printString}"

	is TypeNode -> buildString {
		name?.let(::append)
		names?.joinTo(this, ".")
		arrayCount?.let { append('['); append(it.printString); append(']') }
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

	else -> toString()
}



val Type.printString get(): String = when(this) {
	is ArraySymbol -> "${type.printString}[$size]"
	else -> qualifiedName
}