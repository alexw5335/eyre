package eyre

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
			//it.appendLine("Position/size format: SECTION:POS:IMAGE_POS:IMAGE_ADDRESS, SIZE")
			//it.appendLine()

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
		val sec = context.sections[sym.pos.secIndex]
		append(" --- ")
		append(sec.toString())
		append(' ')
		append(sym.pos.disp.hex)
		if(stage >= CompilerStage.LINK) {
			append(' ')
			append((sec.pos + sym.pos.disp).hex)
			append(' ')
			append((sec.addr + sym.pos.disp).hex)
		}
		if(sym is SizedSym) {
			append("|")
			append(sym.size.hex)
		}
	}



	private fun Appendable.indent(lineNumber: Int) {
		if(lineNumber != 0) {
			val lineNumberString = lineNumber.toString()
			for(j in 0..< lineNumLength - lineNumberString.length)
				append('-')
			append(lineNumberString)
		} else {
			for(i in 0 ..< lineNumLength)
				append(' ')
		}
		for(i in 0 ..< indent + 2)
			append('\t')
	}




	private fun Appendable.appendNode(node: Node) {
		if(node is ScopeEnd) {
			indent--
			appendLine()
			return
		}

		if(node is TopNode)
			indent(node.srcPos.line)
		else
			indent(0)

		when(node) {
			is RegNode    -> appendLine(node.value.toString())
			is IntNode    -> appendLine(node.value.toString())
			is Namespace  -> appendLine("NAMESPACE ${node.place}")

			is OpNode -> {
				node.width?.let {
					append(it.toString())
					append(' ')
				}

				if(node.type.isMem) {
					appendLine("PTR")
					appendChild(node.node)
				} else if(node.type.isImm) {
					appendLine("PTR")
					appendChild(node.node)
				} else {
					appendLine(node.reg.toString())
				}
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
				append("LABEL ${node.place}")
				appendPosInfo(node)
				appendLine()
			}

			is Proc -> {
				append("PROC ")
				append(node.place.toString())
				appendPosInfo(node)
				appendLine()
				indent++
			}


			is InsNode -> {
				append(node.mnemonic.toString())
				appendPosInfo(node)

				if(stage >= CompilerStage.ASSEMBLE) {
					append(" --- ")
					if(node.pos.secIndex != context.textSec.index)
						context.internalErr("Invalid section")
					if(stage >= CompilerStage.LINK) {
						for(i in 0 ..< node.size) {
							append(context.linkWriter.bytes[context.textSec.pos + node.pos.disp + i].hex8)
							if(i != node.size - 1) append(' ')
						}
					} else {
						for(i in 0 ..< node.size) {
							append(context.textWriter.bytes[node.pos.disp + i].hex8)
							if(i != node.size - 1) append(' ')
						}
					}
				}

				appendLine()
				if(node.op1.isNotNone) appendChild(node.op1)
				if(node.op2.isNotNone) appendChild(node.op2)
				if(node.op3.isNotNone) appendChild(node.op3)
				if(node.op4.isNotNone) appendChild(node.op4)
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

		append(sym.place.toString())
		append(':')
		append(sym::class.simpleName)
/*		if(sym is TypedSym) {
			append(':')
			if(sym.type == VoidType)
				append("VoidType")
			else
				append(sym.type.qualifiedName)
		}*/
	}


}