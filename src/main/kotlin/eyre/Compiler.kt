package eyre

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
			printSymbols()
		}

		val resolver = Resolver(context)
		resolver.resolve()

		val assembler = Assembler(context)
		assembler.assemble()
	}



	private fun printHeader(string: String) {
		print("\u001B[32m")
		print(string)
		println("\u001B[0m")
	}




	private fun printTokens(srcFile: SrcFile) {
		printHeader("TOKENS (${srcFile.relPath}):")

		for(i in 0 until srcFile.tokens.size) {
			print("Line ")
			print(srcFile.tokenLines[i])
			print("   ")

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



	private fun printNodes(srcFile: SrcFile) {
		printHeader("NODES (${srcFile.relPath}):")

		for((index, node) in srcFile.nodes.withIndex()) {
			print("Line ")
			print(srcFile.nodeLines[index])
			print("   ")
			println(node.printString)
		}

		println()
	}



	private fun printSymbols() {
		printHeader("SYMBOLS")

		for(symbol in context.symbols.getAll()) {
			when(symbol) {
				is LabelSymbol     -> print("Label       ")
				is Namespace       -> print("Namespace   ")
				is DllSymbol       -> print("DLL         ")
				is DllImportSymbol -> print("DLL import  ")
			}

			if(symbol.scope.isNotEmpty) {
				print(symbol.scope)
				print('.')
			}

			println(symbol.name)
		}
	}


}