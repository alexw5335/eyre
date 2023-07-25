@file:Suppress("RecursivePropertyAccessor")

package eyre

import eyre.gen.NasmEnc
import eyre.gen.VexL
import eyre.gen.VexW
import eyre.util.hexc8



val NasmEnc.compactAvxString get() = buildString {
	if(evex) append("E.") else append("V.")
	when(vexl) {
		VexL.LIG  -> { }
		VexL.L0   -> append("L0.")
		VexL.LZ   -> append("LZ.")
		VexL.L1   -> append("L1.")
		VexL.L128 -> if(Op.X !in ops || Op.Y in ops || Op.Z in ops) append("L128.")
		VexL.L256 -> if(Op.Y !in ops || Op.X in ops || Op.Z in ops) append("L256.")
		VexL.L512 -> if(Op.Z !in ops || Op.X in ops || Op.Y in ops) append("L512.")
	}
	append("${prefix.avxString}.${escape.avxString}")
	when(vexw) {
		VexW.WIG -> append(" ")
		VexW.W0  -> append(".W0 ")
		VexW.W1  -> append(".W1 ")
	}
	append(opcode.hexc8)
	if(hasExt) append("/$ext")
	append("  $mnemonic  ")
	append(opsString)
	append("  ")
	if(pseudo >= 0) append(":$pseudo  ")
	append("$opEnc  ")
	tuple?.let { append("$it ") }
	if(k) if(z) append("KZ ") else append("K ")
	if(sae) append("SAE ")
	if(er) append("ER ")
	when(bcst) {
		0 -> { }
		1 -> append("B16 ")
		2 -> append("B32 ")
		3 -> append("B64 ")
	}
	vsib?.let { append("$it ") }
}



val NasmEnc.printString get() = buildString {
	if(avx) {
		if(evex) append("E.") else append("V.")
		append("${vexl.name}.${prefix.avxString}.${escape.avxString}.${vexw.name} ${opcode.hexc8}")
		if(hasExt) append("/$ext")
		append("  $mnemonic  ")
		append(opsString)
		append("  ")
		if(pseudo >= 0) append(":$pseudo  ")
		append("$opEnc  ")
		tuple?.let { append("$it ") }
		if(k) if(z) append("KZ ") else append("K ")
		if(sae) append("SAE ")
		if(er) append("ER ")
		when(bcst) {
			0 -> { }
			1 -> append("B16 ")
			2 -> append("B32 ")
			3 -> append("B64 ")
		}
		trimEnd()
	} else {
		prefix.string?.let { append("$it ") }
		escape.string?.let { append("$it ") }
		if(opcode and 0xFF00 != 0) append("${(opcode shr 8).hexc8} ")
		append((opcode and 0xFF).hexc8)
		if(hasExt) append("/$ext")
		append("  $mnemonic  ")
		if(ops.isNotEmpty()) append(opsString) else append("NONE")
		append("  ")
		if(rw == 1) append("RW ")
		if(o16 == 1) append("O16 ")
		if(pseudo >= 0) append(":$pseudo ")
		if(mr) append("MR ")
		trimEnd()
	}
}



val OpNode.nasmString get() = buildString {
	when(type) {
		OpNodeType.MEM -> { if(width != null) append("${width.nasmString} "); append("[${node.printString}]") }
		OpNodeType.REG -> append(reg.string)
		OpNodeType.IMM -> append(node.printString)
	}
}



val InsNode.nasmString: String get() = buildString {
	append(mnemonic.string)
	if(op1 == null) return@buildString
	append(" ${op1.nasmString}")
	if(op2 == null) return@buildString
	append(", ${op2.nasmString}")
	if(op3 == null) return@buildString
	append(", ${op3.nasmString}")
	if(op4 == null) return@buildString
	append(", ${op4.nasmString}")
}



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