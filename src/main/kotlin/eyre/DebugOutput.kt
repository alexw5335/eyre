package eyre

import java.io.BufferedWriter
import java.nio.file.*

object DebugOutput {



	fun printTokens(context: CompilerContext) {
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



	fun printNodes(context: CompilerContext) {
		val dir = Paths.get("build").also(Files::createDirectories)

		Files.newBufferedWriter(dir.resolve("nodes.txt")).use {
			for(srcFile in context.srcFiles) {
				it.append(srcFile.relPath.toString())

				if(srcFile.tokens.isEmpty()) {
					it.append(" (empty)")
				} else {
					it.append(":\n")
					printNodes(it, srcFile)
					it.append("\n\n\n")
				}
			}
		}
	}



	fun printTokens(writer: BufferedWriter, srcFile: SrcFile) {
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



	fun printNodes(writer: BufferedWriter, srcFile: SrcFile) {
		for(i in srcFile.nodes.indices) {
			val node = srcFile.nodes[i]
			val lineNumber = node.srcPos?.line ?: error("Missing src position")

			writer.append(lineNumber.toString())
			writer.append(": ")

			when(node) {
				is Label     -> writer.append("LABEL ${node.qualifiedName}")
				is Namespace -> writer.append("NAMESPACE ${node.thisScope}")
				is Proc      -> writer.append("PROC ${node.qualifiedName}")
				else         -> writer.append("$node (TODO: implement debug string)")
			}

			if(i != srcFile.nodes.lastIndex)
				writer.appendLine()
		}
	}


}