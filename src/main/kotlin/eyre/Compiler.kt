package eyre

import java.nio.file.Files
import java.nio.file.Paths

/**
 * Token and node lines aren't working properly
 */
class Compiler(private val context: CompilerContext) {


	fun compile() {
		val lexer = Lexer()
		val parser = Parser(context)

		for(srcFile in context.srcFiles) {
			lexer.lex(srcFile)
			printTokens(srcFile)
			parser.parse(srcFile)
			printNodes(srcFile)
		}
	}



	private fun printHeader(string: String) {
		print("\u001B[32m")
		print(string)
		println("\u001B[0m")
	}



	private fun printTokens(srcFile: SrcFile) {
		printHeader("TOKENS (${srcFile.relPath}):")

		for(i in 0 until srcFile.tokens.size) {
			val line = srcFile.tokenLines[i]
			print("Line ")
			print(line)
			for(j in 0 until (5 - line.toString().length))
				print(' ')

			val token = srcFile.tokens[i]

			val newline = if(srcFile.newlines[i]) 'N' else ' '
			val terminator = if(srcFile.terminators[i]) 'T' else ' '

			when(token) {
				is EndToken    -> println("EOF      ${newline}${terminator}")
				is CharToken   -> println("CHAR     ${newline}${terminator}   \'${token.value}\'")
				is IntToken    -> println("INT      ${newline}${terminator}   ${token.value}")
				is StringToken -> println("STRING   ${newline}${terminator}   \"${token.value}\"")
				is IdToken     -> println("ID       ${newline}${terminator}   ${token.value}")
				is SymToken    -> println("SYM      ${newline}${terminator}   ${token.string}")
			}
		}

		println()
	}



	private fun printNode(node: AstNode, prefix: String) {
		println("Line ${node.srcPos.line}    ${node.printString}")
		for(n in node.getChildren())
			printNode(n, prefix + '\t')
	}



	private fun printNodes(srcFile: SrcFile) {
		printHeader("NODES (${srcFile.relPath}):")

		for(node in srcFile.nodes)
			printNode(node, "")
	}


}