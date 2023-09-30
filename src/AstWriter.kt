package eyre

import java.nio.file.Files

class AstWriter(val context: CompilerContext) {


	private val lineNumLength = lineNumLength()

	private var indent = 0



	private fun lineNumLength(): Int {
		var max = 0

		for(srcFile in context.srcFiles) {
			if(srcFile.tokenLines.size == 0) continue
			max = max.coerceAtLeast(srcFile.tokenLines[srcFile.tokenLines.size - 1])
		}

		var count = 0
		while(max > 0) {
			max /= 10
			count++
		}
		return count
	}



	fun write() {
		Files.newBufferedWriter(context.buildDir.resolve("nodes.txt")).use {
			for(srcFile in context.srcFiles) {
				it.appendLine(srcFile.relPath.toString())
				for(node in srcFile.nodes)
					it.appendNode(node)
				it.appendLine()
				it.appendLine()
			}
		}
	}



	private fun Appendable.appendNode(node: AstNode) {
		if(node is ScopeEnd) {
			indent--
			appendLine()
			return
		}

		val lineNumber = node.srcPos?.line ?: error("Missing src position: $node")

		val lineNumberString = lineNumber.toString()
		for(j in 0..< lineNumLength - lineNumberString.length)
			append('-')
		append(lineNumberString)
		repeat(indent + 2) { append('\t') }

		when(node) {
			is IntNode    -> appendLine(node.value.toString())
			is FloatNode  -> appendLine(node.value.toString())
			is RegNode    -> appendLine(node.value.string)

			is Proc -> {
				appendLine("PROC ${node.qualifiedName}")
				indent++
			}

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

			is UnaryNode -> {
				append("UnaryNode ")
				append(node.op.symbol)
				appendLine()
				indent++
				appendNode(node.node)
				indent--
			}

			is BinaryNode -> {
				append("BinaryNode ")
				append(node.op.string)
				appendLine()
				indent++
				appendNode(node.left)
				appendNode(node.right)
				indent--
			}

			is OpNode -> {
				if(node.isMem) {
					if(node.width != null) {
						append(node.width.string)
						appendLine(" PTR")
					} else {
						appendLine("PTR")
					}
					indent++
					appendNode(node.node)
					indent--
				} else if(node.isImm) {
					if(node.width != null) {
						append(node.width.string)
						appendLine(" IMM")
					} else {
						appendLine("IMM")
					}
					indent++
					appendNode(node.node)
					indent--
				} else {
					appendLine(node.reg.string)
				}
			}

			is Ins -> {
				append("INSTRUCTION: ")
				appendLine(node.mnemonic.string)
				indent++
				if(node.op1 != OpNode.NULL) appendNode(node.op1)
				if(node.op2 != OpNode.NULL) appendNode(node.op2)
				if(node.op3 != OpNode.NULL) appendNode(node.op3)
				if(node.op4 != OpNode.NULL) appendNode(node.op4)
				indent--
			}

			else -> appendLine(node.toString())
		}
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


}