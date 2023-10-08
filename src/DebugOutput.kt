package eyre

import eyre.util.Util
import eyre.util.hexFull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories

object DebugOutput {


	/*
	Util
	 */



	private fun lineCharCount(srcFile: SrcFile) : Int {
		var max = srcFile.tokenLines[srcFile.tokenLines.size - 1]
		var count = 0
		while(max > 0) {
			max /= 10
			count++
		}
		return count
	}



	/*
	Symbols
	 */



	fun writeSymbols(context: Context) {
		val dir = Paths.get("build")
		dir.createDirectories()

		dir.resolve("symbols.txt").bufferedWriter().use {
			for(symbol in context.symbols) {
				if(symbol is PosSym)
					it.append("${symbol::class.simpleName} -- ${symbol.qualifiedName} ${symbol.base.pos} -- ${symbol.base.section} ${context.getSymbolAddress(symbol).hexFull}\n")
				else
					it.append("${symbol::class.simpleName} -- ${symbol.qualifiedName}\n")
			}
		}
	}



	/*
	Tokens
	 */



	fun writeTokens(context: Context) {
		val dir = Paths.get("build").also(Files::createDirectories)

		Files.newBufferedWriter(dir.resolve("tokens.txt")).use {
			for(srcFile in context.srcFiles) {
				it.append(srcFile.relPath.toString())

				if(srcFile.tokens.isEmpty()) {
					it.append(" (empty)")
				} else {
					it.append(":\n")
					printTokens(it, srcFile)
					it.append("\n\n\n")
				}
			}
		}
	}



	private fun printTokens(writer: Appendable, srcFile: SrcFile) {
		for(i in srcFile.tokens.indices) {
			val lineNumber = srcFile.tokenLines[i]
			val token = srcFile.tokens[i]
			val newline = srcFile.newlines[i]
			val terminator = srcFile.terminators[i]

			writer.append(lineNumber.toString())
			writer.append(": ")

			when(token) {
				is CharToken   -> writer.append("\'${token.value}\'")
				is FloatToken  -> writer.append(token.value.toString())
				is IntToken    -> writer.append(token.value.toString())
				is Name        -> writer.append("${token.string} (${token.id})")
				is RegToken    -> writer.append(token.value.string)
				is StringToken -> writer.append("\"${token.value.replace("\n", "\\n")}\"")
				is SymToken    -> writer.append(token.string)
				EndToken       -> break // Already handled
			}

			if(newline || terminator) {
				writer.append("   ")
				if(newline) writer.append("N")
				if(terminator) writer.append("T")
			}

			if(i != srcFile.tokens.lastIndex && srcFile.tokens[i+1] == EndToken)
				break

			writer.appendLine()
		}
	}



	/*
	Nodes
	 */



	fun writeNodes(context: Context) {
		val dir = Paths.get("build").also(Files::createDirectories)

		Files.newBufferedWriter(dir.resolve("nodes.txt")).use {
			for(srcFile in context.srcFiles) {
				it.append(srcFile.relPath.toString())

				if(srcFile.nodes.isEmpty()) {
					it.append(" (empty)")
				} else {
					it.append(":\n")
					printNodes(it, srcFile)
					it.append("\n\n\n")
				}
			}
		}
	}



	private fun printNodes(writer: Appendable, srcFile: SrcFile) {
		var indent = 0
		val lineCharCount = lineCharCount(srcFile)

		fun printNode(node: Node) {
			if(node is ScopeEnd) {
				indent--
				writer.appendLine()
				return
			}

			val lineNumber = node.srcPos?.line ?: error("Missing src position: $node")

			val lineNumberString = lineNumber.toString()
			for(j in 0..< lineCharCount - lineNumberString.length)
				writer.append(' ')
			writer.append(lineNumberString)
			writer.append("    ")

			for(j in 0 ..< indent)
				writer.append("    ")

			writer.appendNode(node)
			writer.appendLine()

			if(node is Proc)
				indent++
			else if(node is Namespace)
				writer.appendLine()
			else if(node is Enum) {
				indent++
				for(entry in node.entries)
					printNode(entry)
				indent--
				writer.appendLine()
			}
		}

		for(n in srcFile.nodes)
			printNode(n)
	}



	fun printString(node: Node) = buildString { appendNode(node) }



	/**
	 * Appends a single-line string representation of a node
	 */
	private fun Appendable.appendNode(node: Node) { when(node) {
		is IntNode    -> append(node.value.toString())
		is FloatNode  -> append(node.value.toString())
		is NameNode   -> append(node.value.string)
		is RegNode    -> append(node.value.string)

		is StringNode -> {
			append('\"')
			append(node.value.replace("\n", "\\n"))
			append("\"")
		}

		is Label -> {
			append("LABEL ")
			append(node.qualifiedName)
		}

		is Namespace -> {
			append("NAMESPACE ")
			append(node.thisScope.toString())
		}

		is Proc -> {
			append("PROC ")
			append(node.qualifiedName)
			if(node.parts.isNotEmpty()) {
				append('(')
				for((index, part) in node.parts.withIndex()) {
					appendNode(part)
					if(index != node.parts.lastIndex)
						append(", ")
				}
				append(')')
			}
		}

		is UnNode  -> {
			append('(')
			append(node.op.string)
			appendNode(node.node)
			append(')')
		}

		is BinNode -> {
			append('(')
			appendNode(node.left)
			append(node.op.string)
			appendNode(node.right)
			append(')')
		}

		is OpNode -> {
			if(node.isMem) {
				if(node.width != null) {
					append(node.width.string)
					append(' ')
				}
				append('[')
				appendNode(node.node)
				append(']')
			} else if(node.isImm) {
				if(node.width != null) {
					append(node.width.string)
					append(' ')
				}
				appendNode(node.node)
			} else {
				append(node.reg.string)
			}
		}

		is Ins -> {
			append(node.mnemonic.string)
			if(node.opCount == 0) return
			append(' ')
			appendNode(node.op1)
			if(node.opCount == 1) return
			append(", ")
			appendNode(node.op2)
			if(node.opCount == 2) return
			append(", ")
			appendNode(node.op3)
			if(node.opCount == 3) return
			append(", ")
			appendNode(node.op4)
		}

		is EnumEntry -> {
			append(node.qualifiedName)
			if(node.valueNode != null) {
				append(" = ")
				appendNode(node.valueNode)
			}
		}

		is Enum -> {
			append("ENUM ")
			append(node.qualifiedName)
		}

		is Const -> {
			append("CONST ")
			append(node.qualifiedName)
			append(" = ")
			appendNode(node.valueNode)
		}

		else -> {
			append(node.toString())
			append(" TODO: Implement debug string")
		}
	} }



	/*
	Disassembly
	 */



	private fun printHeader(string: String) {
		print("\u001B[32m")
		print(string)
		println("\u001B[0m")
	}



	fun writeDisassembly(context: Context) {
		val params = arrayOf("objdump", "-M", "intel-mnemonic", "-d", "build/test.exe")
		val process = Runtime.getRuntime().exec(params)
		val reader = BufferedReader(InputStreamReader(process.inputStream))

		val lines = ArrayList<String>()

		while(true) reader.readLine()?.let(lines::add) ?: break

		process.errorReader().readText().let {
			if(it.isNotEmpty())
				print(it)
		}

		process.waitFor()

		val buildDir = Paths.get("build").also(Files::createDirectories)
		Files.write(buildDir.resolve("disasm.txt"), lines)

		disassemble(context)
	}



	fun disassemble(context: Context) {
		printHeader("DISASSEMBLY")

		val buildDir = Paths.get("build")
		buildDir.createDirectories()

		for(symbol in context.symbols) {
			if(symbol !is Proc || symbol.section != Section.TEXT) continue

			val pos = context.getPos(symbol.section) + symbol.pos

			println()
			printHeader("${symbol.qualifiedName} ($pos, ${symbol.size})")
			Files.write(buildDir.resolve("code.bin"), context.linkWriter.getTrimmedBytes(pos, symbol.size))
			Util.run("ndisasm", "-b64", "build/code.bin")
		}
	}


}