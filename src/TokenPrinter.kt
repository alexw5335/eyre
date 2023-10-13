package eyre

import java.nio.file.Files

class TokenPrinter(private val context: Context) {


	fun print() {
		val dir = context.buildDir

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
		var lineCount = 1

		for(i in 0 ..< srcFile.tokens.size - 1) {
			val token = srcFile.tokens[i]

			writer.append(lineCount.toString())
			writer.append(": ")

			if(token == SymToken.NEWLINE) lineCount++

			when(token) {
				is CharToken   -> writer.append("\'${token.value}\'")
				is IntToken    -> writer.append(token.value.toString())
				is Name        -> writer.append("${token.string} (${token.id})")
				is RegToken    -> writer.append(token.value.string)
				is StringToken -> writer.append("\"${token.value.replace("\n", "\\n")}\"")
				is SymToken    -> writer.append(token.string)
				is FloatToken  -> writer.append(token.value.toString())
			}

			writer.appendLine()
		}
	}


}