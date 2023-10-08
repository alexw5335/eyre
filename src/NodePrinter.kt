package eyre

import eyre.util.hex
import eyre.util.hex8
import java.nio.file.Files

class NodePrinter(private val context: Context, private val stage: CompilerStage) {


	private var lineNumLength = 0

	private var indent = 0

	private var prevLine = 0



	private fun lineNumLength(file: SrcFile): Int {
		var max = file.lineCount
		var count = 0
		while(max > 0) {
			max /= 10
			count++
		}
		return count
	}



	fun print() {
		lineNumLength = context.srcFiles.maxOf(::lineNumLength)

		Files.newBufferedWriter(context.buildDir.resolve("nodes.txt")).use {
			it.appendLine("Position/size format: SECTION:POS:IMAGE_POS:IMAGE_ADDRESS, SIZE")
			it.appendLine()

			for(srcFile in context.srcFiles) {
				indent = 0
				it.appendLine(srcFile.relPath.toString())
				for(node in srcFile.nodes)
					it.appendNode(node)
				it.appendLine()
				it.appendLine()
			}
		}
	}



	private fun Appendable.appendChild(node: Node) {
		indent++
		appendNode(node)
		indent--
	}



	private fun Appendable.appendPosInfo(sym: PosSym) {
		if(stage < CompilerStage.ASSEMBLE) return
		append(" --- ")
		append(sym.section.toString())
		append(':')
		append(sym.pos.hex)
		append(':')
		append(context.getTotalPos(sym).hex)
		append(':')
		append(context.getTotalAddr(sym).hex)
		if(sym is SizedSym) {
			append(':')
			append(sym.size.hex)
		}
	}



	private fun Appendable.writeIndent() {
		for(i in 0 ..< lineNumLength)
			append(' ')
		for(i in 0 ..< indent + 2)
			append('\t')
	}



	private fun Appendable.appendNode(node: Node) {
		if(node is ScopeEnd) {
			indent--
			return
		}

		val line = node.srcPos!!.line
		if(line - prevLine > 1) appendLine()
		prevLine = line

		val lineNumberString = line.toString()
		for(j in 0..< lineNumLength - lineNumberString.length)
			append(' ')
		append(lineNumberString)
		repeat(indent + 2) { append('\t') }

		when(node) {
			is RegNode    -> appendLine(node.value.toString())
			is IntNode    -> appendLine(node.value.toString())
			is FloatNode  -> appendLine(node.value.toString())
			is Namespace  -> appendLine("NAMESPACE ${node.qualifiedName}")

			is Member -> {
				append(node.offset.toString())
				append(' ')
				append(node.type.name.string)
				append(' ')
				append(node.name.string)
				appendLine()
			}

			is Struct -> {
				append("STRUCT ")
				append(node.qualifiedName)
				append(" --- ")
				append(node.size.toString())
				appendLine()
				for(member in node.members) appendChild(member)
			}

			is TypeNode -> {
				if(node.name != null) {
					append(node.name.string)
				} else if(node.names != null) {
					for(i in node.names.indices) {
						append(node.names[i].string)
						if(i != node.names.lastIndex) append('.')
					}
				}

				if(node.arraySizes != null) {
					for(n in node.arraySizes)
						append("[]")
					appendLine()
					for(n in node.arraySizes)
						appendChild(n)
				} else {
					appendLine()
				}
			}

			is DotNode -> {
				append("DOT")
				appendSym(node.sym)
				appendLine()
				appendChild(node.left)
				indent++
				writeIndent()
				appendLine(node.right.string)
				indent--
			}

			is Var -> with(node) {
				append("VAR ")
				append(qualifiedName)
				appendPosInfo(node)
				appendSym(node.type)
				appendLine()
				if(node.typeNode != null) appendChild(node.typeNode)
				if(node.valueNode != null) appendChild(node.valueNode)
			}

			is NameNode -> {
				append(node.value.string)
				appendSym(node.sym)
				appendLine()
			}

			is StringNode -> {
				append('"')
				append(node.value.replace("\n", "\\n"))
				append('"')
				appendLine()
			}

			is UnNode -> {
				appendLine(node.op.string)
				appendChild(node.node)
			}

			is BinNode -> {
				appendLine(node.op.string)
				appendChild(node.left)
				appendChild(node.right)
			}

			is Label -> {
				append("LABEL ${node.qualifiedName}")
				appendPosInfo(node)
				appendLine()
			}

			is Proc -> {
				append("PROC ")
				append(node.qualifiedName)
				appendPosInfo(node)
				appendLine()
				indent++
			}

			is OpNode -> {
				if(node.isMem) {
					if(node.width != null) {
						append(node.width.string)
						appendLine(" PTR")
					} else {
						appendLine("PTR")
					}
					appendChild(node.node)
				} else if(node.isImm) {
					if(node.width != null) {
						append(node.width.string)
						appendLine(" IMM")
					} else {
						appendLine("IMM")
					}
					appendChild(node.node)
				} else {
					appendLine(node.reg.string)
				}
			}

			is Ins -> {
				append(node.mnemonic.toString())
				appendPosInfo(node)

				if(stage >= CompilerStage.ASSEMBLE) {
					append(" --- ")
					if(stage >= CompilerStage.LINK) {
						for(i in 0 ..< node.size) {
							append(context.linkWriter.bytes[context.getPos(node.section) + node.pos + i].hex8)
							if(i != node.size - 1) append(' ')
						}
					} else {
						for(i in 0 ..< node.size) {
							append(context.textWriter.bytes[node.pos + i].hex8)
							if(i != node.size - 1) append(' ')
						}
					}

				}

				appendLine()
				if(node.op1 != OpNode.NULL) appendChild(node.op1)
				if(node.op2 != OpNode.NULL) appendChild(node.op2)
				if(node.op3 != OpNode.NULL) appendChild(node.op3)
				if(node.op4 != OpNode.NULL) appendChild(node.op4)
			}

			else -> appendLine(node.toString())
		}
	}



	private fun Appendable.appendSym(sym: Sym?) {
		append(" --- ")

		if(sym == null) {
			append("*NULL*")
			return
		}

		append(sym.qualifiedName)
		append(':')
		append(sym::class.simpleName)
		if(sym is TypedSym) {
			append(':')
			if(sym.type == VoidType)
				append("VoidType")
			else
				append(sym.type.qualifiedName)
		}
	}


}