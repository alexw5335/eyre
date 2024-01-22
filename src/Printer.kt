package eyre

import java.io.BufferedWriter
import java.nio.file.Files

class Printer(private val context: Context, private val stage: EyreStage) {


	private val atResolve = stage >= EyreStage.RESOLVE



	private fun BufferedWriter.appendLineNumber(lineNumber: Int) {
		var count = 0
		var mutable = lineNumber
		while(mutable != 0) { mutable /= 10; count++ }
		append(lineNumber.toString())
		for(i in 0 ..< 8 - count) append(' ')
	}



	/*
	Misc.
	 */



	fun writeText() {
		Files.write(context.buildDir.resolve("code.bin"), context.textWriter.copy())
	}




	/*
	Tokens
	 */



	fun writeTokens() {
		Files.newBufferedWriter(context.buildDir.resolve("tokens.txt")).use {
			for(srcFile in context.files) {
				it.append(srcFile.relPath.toString())

				if(srcFile.tokens.isEmpty()) {
					it.append(" (empty)")
				} else {
					it.append(":\n")
					it.appendTokens(srcFile.tokens)
					it.append("\n\n\n")
				}
			}
		}
	}



	private fun BufferedWriter.appendTokens(tokens: List<Token>) {
		for(t in tokens) {
			appendLineNumber(t.line)

			when(t.type) {
				TokenType.REG     -> append("REG     ${t.regValue}")
				TokenType.NAME    -> append("NAME    ${t.nameValue}")
				TokenType.STRING  -> append("STRING  \"${t.stringValue(context)}\"")
				TokenType.INT     -> append("INT     ${t.value}")
				TokenType.CHAR    -> append("CHAR    \'${t.value.toChar()}\'")
				else              -> append(t.type.name)
			}

			appendLine()
		}
	}



	/*
	Symbols
	 */



	fun writeSymbols() {
		Files.newBufferedWriter(context.buildDir.resolve("symbols.txt")).use {
			for(sym in context.symTable.list)
				if(sym != RootSym)
					it.appendLine("${sym::class.simpleName}  --  ${sym.fullName}")
		}
	}



	/*
	Nodes
	 */



	private var indent = 0



	fun writeNodes() {
		Files.newBufferedWriter(context.buildDir.resolve("nodes.txt")).use {
			for(srcFile in context.files) {
				it.append(srcFile.relPath.toString())

				if(srcFile.nodes.isEmpty()) {
					it.append(" (empty)")
				} else {
					it.append(":\n")
					for(node in srcFile.nodes)
						it.appendNode(node)
					it.append("\n\n\n")
				}
			}
		}
	}



	private fun BufferedWriter.appendChild(node: Node) {
		indent++
		appendNode(node)
		indent--
	}



	private fun shouldIndent(node: Node) = when(node) {
		is ProcNode -> true
		else -> false
	}



	private val Symbol.fullName: String get() = if(name.isNull)
		"_"
	else
		context.qualifiedName(this)


	private fun BufferedWriter.printType(type: Type) {
		if(type is ArrayType) {
			printType(type.base)
			append('[')
			append(type.count.toString())
			append(']')
		} else {
			append(type.fullName)
		}
	}



	private fun BufferedWriter.appendNode(node: Node) {
		if(node is ScopeEndNode) {
			if(shouldIndent(node.origin))
				indent--
			return
		}

		appendLineNumber(node.srcPos?.line ?: context.internalErr("Missing src pos line: $node"))

		for(i in 0 ..< indent)
			append("    ")

		when(node) {
			is OpNode -> {
				if(node.type == OpType.MEM) {
					appendLine("${node.width.opString}ptr")
					appendChild(node.child)
				} else if(node.type == OpType.IMM) {
					appendLine("${node.width.opString}imm")
					appendChild(node.child)
				} else {
					appendLine(node.reg.string)
				}
			}

			is InsNode -> {
				appendLine(node.mnemonic.string)
				node.op1?.let { appendChild(it) }
				node.op2?.let { appendChild(it) }
				node.op3?.let { appendChild(it) }
				node.op4?.let { appendChild(it) }
			}

			is UnNode -> {
				appendLine(node.op.string)
				appendChild(node.child)
			}

			is BinNode -> {
				appendLine(node.op.string)
				appendChild(node.left)
				appendChild(node.right)
			}

			is ConstNode -> {
				append("CONST ${context.qualifiedName(node)}")
				if(atResolve)
					append(" (value = ${node.intValue})")
				appendLine()
				appendChild(node.valueNode)
			}

			is EnumEntryNode -> {
				append(context.qualifiedName(node))
				if(atResolve)
					append(" (value = ${node.intValue})")
				appendLine()
				node.valueNode?.let { appendChild(it) }
			}

			is EnumNode -> {
				appendLine("ENUM ${context.qualifiedName(node)}")
				for(child in node.entries)
					appendChild(child)
			}

			is TypedefNode -> {
				append("TYPEDEF ${node.fullName}")
				if(atResolve) {
					append(" (type = ")
					printType(node.type)
					append(')')
				}
				appendLine()
				appendChild(node.typeNode)
			}

			is DotNode -> {
				appendLine(".")
				appendChild(node.left)
				appendChild(node.right)
			}

			is ArrayNode -> {
				appendLine("[]")
				appendChild(node.left)
				appendChild(node.right)
			}

			is RefNode -> {
				appendLine("::")
				appendChild(node.left)
				appendChild(node.right)
			}

			is MemberNode -> {
				append("${node.fullName} (type = ")
				printType(node.type)
				appendLine(", offset = ${node.offset})")
				if(node.typeNode != null)
					appendChild(node.typeNode)
				else
					appendChild(node.struct!!)
			}

			is StructNode -> {
				appendLine("STRUCT ${node.fullName} (size = ${node.size})")
				for(member in node.members)
					appendChild(member)
			}

			is TypeNode -> {
				append(node.names.joinToString(separator = "."))
				for(size in node.arraySizes)
					append("[]")
				appendLine()
				for(size in node.arraySizes)
					appendChild(size)
			}

			is InitNode -> {
				appendLine("INITIALISER")
				for(element in node.elements)
					appendChild(element)
			}

			is VarNode -> {
				appendLine("VAR ${node.fullName}")
				appendChild(node.typeNode)
				node.valueNode?.let { appendChild(it) }
			}

			is ProcNode      -> appendLine("PROC ${context.qualifiedName(node)}")
			is RegNode       -> appendLine(node.value.string)
			is NameNode      -> appendLine(node.value.string)
			is IntNode       -> appendLine("${node.value}")
			is LabelNode     -> appendLine("LABEL ${context.qualifiedName(node)}")
			is NamespaceNode -> appendLine("NAMESPACE ${context.qualifiedName(node)}")
			else             -> appendLine(node::class.simpleName)
		}

		if(shouldIndent(node))
			indent++
	}


}