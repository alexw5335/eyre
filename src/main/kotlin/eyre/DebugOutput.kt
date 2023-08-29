package eyre

import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Paths

object DebugOutput {


	fun printTokens(context: CompilerContext) {
		val dir = Paths.get("build/tokens")
		Files.deleteIfExists(dir)
		Files.createDirectories(dir)

		for(srcFile in context.srcFiles) {
			Files.newBufferedWriter(dir.resolve(srcFile.relPath)).use {
				printTokens(it, srcFile)
			}
		}
	}



	fun printTokens(writer: BufferedWriter, srcFile: SrcFile) {
		for(i in srcFile.tokens.indices) {
			val lineNumber = srcFile.tokenLines[i]
			val token = srcFile.tokens[i]

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

			if(i != srcFile.tokens.lastIndex && srcFile.tokens[i+1] == EndToken)
				break

			writer.appendLine()
		}
	}


}