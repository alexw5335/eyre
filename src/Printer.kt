package eyre

import java.nio.file.Files

class Printer(private val context: Context) {


	/*
	Variables
	 */



	val tokensBuilder = StringBuilder()

	val nodesBuilder = StringBuilder()

	var indent = 0



	/*
	Util
	 */



	private val Sym.fullName: String get() = if(name.isNull)
		"_"
	else
		context.qualifiedName(this)

	private fun StringBuilder.appendLineNumber(lineNumber: Int) {
		var count = 0
		var mutable = lineNumber
		while(mutable != 0) { mutable /= 10; count++ }
		append(lineNumber)
		for(i in 0 ..< 8 - count) append(' ')
	}

	private val String.printable: String get() = replace("\n", "\\n")

	private val Char.escape: String get() = when(this) {
		'\n'    -> "\\n"
		'\t'    -> "\\t"
		'\r'    -> "\\r"
		Char(0) -> "\\0"
		else    -> toString()
	}

	fun write(path: String, builder: StringBuilder) =
		Files.writeString(context.buildDir.resolve(path), builder.toString())

	fun printDisasm() {
		val path = context.buildDir.resolve("code.bin")
		Files.write(path, context.linkWriter.copy(context.textSec.pos, context.textSec.size))
		Util.run("ndisasm", "-b64", path.toString())
	}



	/*
	Tokens
	 */



	fun appendTokens(srcFile: SrcFile, tokens: List<Token>) =
		tokensBuilder.appendTokens(srcFile, tokens)

	fun StringBuilder.appendTokens(srcFile: SrcFile, tokens: List<Token>) {
		append(srcFile.name)

		if(tokens.isEmpty()) {
			append(" (empty)")
		} else {
			append(":\n")
			for(token in tokens) {
				appendLineNumber(token.line)

				when(token.type) {
					TokenType.NAME    -> appendLine(token.nameValue.string)
					TokenType.STRING  -> appendLine("\"${token.stringValue.replace("\n", "\\n")}\"")
					TokenType.INT     -> appendLine(token.intValue)
					TokenType.CHAR    -> appendLine(Char(token.intValue.toInt()).escape)
					else              -> appendLine(token.type.string)
				}
			}
			append("\n\n")
		}
	}



	/*
	Nodes
	 */



	fun appendNodes(srcFile: SrcFile) =
		nodesBuilder.appendNodes(srcFile)



	fun StringBuilder.appendNodes(srcFile: SrcFile) {
		append(srcFile.name)

		if(srcFile.nodes.isEmpty()) {
			append(" (empty)")
		} else {
			append(":\n")
			for(node in srcFile.nodes)
				appendNode(node)
			append("\n\n\n")
		}
	}



	private fun StringBuilder.appendChild(node: Node) {
		indent++
		appendNode(node)
		indent--
	}



	private fun StringBuilder.appendChildren(nodes: List<Node>) {
		indent++
		for(node in nodes) appendNode(node)
		indent--
	}



	fun StringBuilder.appendNode(node: Node) {
		if(node is ScopeEndNode) {
			if(node.sym !is NamespaceNode)
				indent--
			return
		}

		appendLineNumber(node.srcPos?.line ?: context.internalErr())
		for(i in 0 ..< indent) append("    ")

		when(node) {
			is DllImportNode -> appendLine("DLLIMPORT ${node.dllName} ${node.import.name}")
			is FunNode -> {
				append("fun ${node.fullName}(")
				for((i, param) in node.params.withIndex()) {
					append(param.name)
					append(": ")
					appendExpr(param.typeNode!!)
					if(i != node.params.lastIndex) append(", ")
				}
				append(")")
				if(node.returnTypeNode != null) {
					append(": ")
					appendExpr(node.returnTypeNode!!)
				}
				appendLine()
				indent++
			}
			is EnumNode -> {
				appendLine("enum ${node.fullName}")
				appendChildren(node.entries)
			}
			is EnumEntryNode -> {
				append(node.name)
				if(node.valueNode != null) {
					append(" = ")
					appendExpr(node.valueNode)
				}
				appendLine()
			}
			is MemberNode -> {
				if(node.struct != null) {
					appendLine("struct ${node.fullName}")
					appendChildren(node.struct.members)
				} else {
					append("${node.name}: ")
					appendExpr(node.typeNode!!)
					appendLine()
				}
			}
			is StructNode -> {
				appendLine("struct ${node.fullName}")
				appendChildren(node.members)
			}
			is NamespaceNode -> {
				appendLine("namespace ${node.fullName}")
				indent++
			}
			is VarNode -> {
				append("var ")
				append(node.fullName)
				if(node.typeNode != null) {
					append(": ")
					appendExpr(node.typeNode)
				}
				if(node.valueNode != null) {
					append(" = ")
					appendExpr(node.valueNode)
				}
				appendLine()
			}
			is CallNode -> { appendExpr(node); appendLine() }
			is ArrayNode -> { appendExpr(node); appendLine() }
			is DotNode -> { appendExpr(node); appendLine() }
			is BinNode -> { appendExpr(node); appendLine() }
			else -> appendLine(node)
		}
	}

	private fun StringBuilder.appendExpr(node: Node) { when(node) {
		is IntNode -> append(node.value.toString())
		is StringNode -> append("\"${node.value.printable}\"")
		is NameNode -> append(node.name.string)

		is TypeNode -> {
			append(node.names.joinToString(separator = "."))
			for(mod in node.mods) when(mod) {
				is TypeNode.PointerMod -> append("*")
				is TypeNode.ArrayMod -> {
					append('[')
					mod.sizeNode?.let { appendExpr(it) }
					append(']')
				}
			}
		}

		is RefNode -> {
			appendExpr(node.left)
			append("::")
			appendExpr(node.right)
		}

		is DotNode -> {
			append('(')
			appendExpr(node.left)
			append('.')
			appendExpr(node.right)
			append(')')
		}

		is ArrayNode -> {
			append('(')
			appendExpr(node.left)
			append('[')
			appendExpr(node.right)
			append(']')
			append(')')
		}

		is BinNode -> {
			append('(')
			appendExpr(node.left)
			append(' ')
			append(node.op.string)
			append(' ')
			appendExpr(node.right)
			append(')')
		}

		is UnNode -> {
			append('(')
			if(!node.op.isPostfix) append(node.op.string)
			appendExpr(node.child)
			if(node.op.isPostfix) append(node.op.string)
			append(')')
		}

		is CallNode -> {
			append('(')
			appendExpr(node.left)
			append('(')
			for(i in 0 ..< node.args.size - 1) {
				appendExpr(node.args[i])
				append(", ")
			}
			if(node.args.isNotEmpty())
				appendExpr(node.args[0])
			append(')')
			append(')')
		}

		else -> context.internalErr("Non-printable expression node: $node")
	}}



}