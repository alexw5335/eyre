package eyre

import java.io.BufferedWriter
import java.nio.file.Files

class Printer(private val context: Context, private val stage: EyreStage) {


	private fun BufferedWriter.appendLineNumber(lineNumber: Int) {
		var count = 0
		var mutable = lineNumber
		while(mutable != 0) { mutable /= 10; count++ }
		append(lineNumber.toString())
		for(i in 0 ..< 8 - count) append(' ')
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
		var lineNumber = 1
		for(t in tokens) {
			if(t.type == TokenType.NEWLINE) {
				lineNumber++
				continue
			}

			appendLineNumber(lineNumber)

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

				if(node.count > 0) {
					appendChild(node.op1)
					if(node.count > 1) {
						appendChild(node.op2)
						if(node.count > 2) {
							appendChild(node.op3)
							if(node.count > 3) {
								appendChild(node.op4)
							}
						}
					}
				}
			}

			is UnNode -> {
				appendLine("UNARY ${node.op.string}")
				appendChild(node.child)
			}

			is BinNode -> {
				appendLine("BINARY ${node.op.string}")
				appendChild(node.left)
				appendChild(node.right)
			}

			is ConstNode -> {
				appendLine("CONST ${context.qualifiedName(node)}")
				appendChild(node.valueNode)
			}

			is EnumEntryNode -> {
				appendLine(context.qualifiedName(node))
			}

			is EnumNode -> {
				appendLine("ENUM ${context.qualifiedName(node)}")
				for(child in node.entries)
					appendChild(child)
			}

			is ProcNode      -> appendLine("PROC ${context.qualifiedName(node)}")
			is RegNode       -> appendLine(node.value.string)
			is NameNode      -> appendLine(node.value.string)
			is IntNode       -> appendLine("INT ${node.value}")
			is LabelNode     -> appendLine("LABEL ${context.qualifiedName(node)}")

			is NamespaceNode -> appendLine("NAMESPACE ${context.qualifiedName(node)}")
			else             -> appendLine(node::class.simpleName)
		}

		if(shouldIndent(node))
			indent++
	}


}