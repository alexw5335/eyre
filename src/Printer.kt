package eyre

import java.io.BufferedWriter
import java.nio.file.Files

class Printer(private val context: Context) {


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



	fun writeNodes() {
		Files.newBufferedWriter(context.buildDir.resolve("nodes.txt")).use {
			for(srcFile in context.files) {
				it.append(srcFile.relPath.toString())

				if(srcFile.nodes.isEmpty()) {
					it.append(" (empty)")
				} else {
					it.append(":\n")
					it.appendNodes(srcFile.nodes)
					it.append("\n\n\n")
				}
			}
		}
	}



	private fun BufferedWriter.appendNodes(nodes: List<Node>) {
		for(n in nodes) {
			if(n.srcPos == null) context.internalErr("Missing src pos line: $n")
			appendLineNumber(n.srcPos!!.line)
			appendNode(n)
			appendLine()
		}
	}



	private fun BufferedWriter.appendNode(node: Node) { when(node) {
		is NameNode -> append(node.value.string)
		else -> append(node::class.simpleName)
	}}


}