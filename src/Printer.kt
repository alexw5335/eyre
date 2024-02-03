package eyre

import java.nio.file.Files

class Printer(private val context: Context) {


	/*
	Util
	 */



	private fun StringBuilder.appendLineNumber(lineNumber: Int) {
		var count = 0
		var mutable = lineNumber
		while(mutable != 0) { mutable /= 10; count++ }
		append(lineNumber)
		for(i in 0 ..< 8 - count) append(' ')
	}



	private fun write(path: String, builder: StringBuilder.() -> Unit) =
		Files.writeString(context.buildDir.resolve(path), buildString(builder))



	/*
	Misc.
	 */



	fun writeDisasm() {
		val path = context.buildDir.resolve("code.bin")
		Files.write(path, context.textWriter.copy())
		Util.run("ndisasm", "-b64", path.toString())
	}



	/*
	Tokens
	 */



	fun writeTokens() {
		write("tokens.txt") {
			for(srcFile in context.files) {
				append(srcFile.relPath.toString())

				if(srcFile.tokens.isEmpty()) {
					append(" (empty)")
				} else {
					append(":\n")
					appendTokens(srcFile.tokens)
					append("\n\n\n")
				}
			}
		}
	}



	private fun StringBuilder.appendTokens(tokens: List<Token>) {
		for(t in tokens) {
			appendLineNumber(t.line)

			when(t.type) {
				TokenType.NAME    -> appendLine("NAME    ${t.nameValue}")
				TokenType.STRING  -> appendLine("STRING  \"${t.stringValue}\"")
				TokenType.INT     -> appendLine("INT     ${t.intValue}")
				TokenType.CHAR    -> appendLine("CHAR    \'${t.intValue.toChar()}\'")
				TokenType.REG     -> appendLine("REG     ${t.regValue}")
				else              -> appendLine(t.type.name)
			}
		}
	}



	/*
	Symbols
	 */



	private val Sym.fullName: String get() = if(name.isNull)
		"_"
	else
		context.qualifiedName(this)



	fun writeSymbols() {
		write("symbols.txt") {
			for(sym in context.symTable.list)
				appendLine("${sym::class.simpleName}  --  ${sym.fullName}")
		}
	}



	/*
	Nodes
	 */



	private var indent = 0



	fun writeNodes() {
		write("nodes.txt") {
			for(srcFile in context.files) {
				append(srcFile.relPath.toString())

				if(srcFile.nodes.isEmpty()) {
					append(" (empty)")
				} else {
					append(":\n")
					for(node in srcFile.nodes)
						appendNode(node)
					append("\n\n\n")
				}
			}
		}
	}



	private fun StringBuilder.appendChild(node: Node) {
		indent++
		appendNode(node)
		indent--
	}



	private fun StringBuilder.appendNode(node: Node) {
		appendLineNumber(node.srcPos?.line ?: context.internalErr())
		for(i in 0 ..< indent) append("    ")

		when(node) {
			is RegNode  -> appendLine(node.value)
			is NameNode -> appendLine(node.value.string)
			is IntNode  -> appendLine("${node.value}")

			is OpNode -> {
				when(node.type) {
					OpType.IMM -> {
						appendLine("IMM")
						appendChild(node.child!!)
					}
					OpType.MEM -> {
						if(node.width != Width.NONE)
							appendLine("${node.width} MEM")
						else
							appendLine("MEM")
						appendChild(node.child!!)
					}
					else -> appendLine(node.reg)
				}
			}

			is InsNode -> {
				appendLine(node.mnemonic)
				node.op1?.let { appendChild(it) }
				node.op2?.let { appendChild(it) }
				node.op3?.let { appendChild(it) }
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

			is ProcNode -> {
				appendLine("PROC ${node.sym.fullName}")
				indent++
				node.children.forEach { appendNode(it) }
				indent--
			}

			else -> appendLine(node::class.simpleName)
		}
	}


}