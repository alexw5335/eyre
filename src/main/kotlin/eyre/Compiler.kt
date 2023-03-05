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
			//printTokens(srcFile)
			parser.parse(srcFile)
			printNodes(srcFile)
			//printSymbols()
		}

		//val resolver = Resolver(context)
		//resolver.resolve()

		//val assembler = Assembler(context)
		//assembler.assemble()

		//val linker = Linker(context)
		//linker.link()
		//Files.write(Paths.get("test.exe"), context.linkWriter.getTrimmedBytes())
		//dumpbin()
		//disassemble()
	}



	private fun run(vararg params: String) {
		val process = Runtime.getRuntime().exec(params)
		process.waitFor()
		process.errorReader().readText().let {
			if(it.isNotEmpty()) {
				print("\u001B[31m$it\\u001B[0m")
				error("Process failed")
			}
		}

		process.inputReader().readText().let {
			if(it.isNotEmpty()) print(it)
		}
	}



	private fun dumpbin() {
		printHeader("DUMPBIN")
		run("dumpbin", "/ALL", "test.exe")
	}



	private fun disassemble() {
		val pos = context.sections[Section.TEXT.ordinal]!!.pos
		val size = context.sections[Section.TEXT.ordinal]!!.size
		Files.write(Paths.get("test.bin"), context.linkWriter.getTrimmedBytes(pos, size))
		printHeader("DISASSEMBLY")
		run("ndisasm", "-b64", "test.bin")
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
				is ErrorToken  -> println("ERROR")
			}
		}

		println()
	}



	private fun printNodes(srcFile: SrcFile) {
		printHeader("NODES (${srcFile.relPath}):")

		for(node in srcFile.nodes) {
			print("Line ")
			print(node.srcPos.line)
			for(i in 0 until (5 - node.srcPos.line.toString().length))
				print(' ')
			println(node.printString)
		}

		println()
	}



	private fun printSymbols() {
		printHeader("SYMBOLS")

		for(symbol in context.symbols.getAll()) {
			when(symbol) {
				is LabelSymbol     -> print("LABEL       ")
				is Namespace       -> print("NAMESPACE   ")
				is DllSymbol       -> print("DLL         ")
				is DllImportSymbol -> print("DLL IMPORT  ")
				is VarSymbol       -> print("VAR         ")
				is ResSymbol       -> print("RES         ")
				is ConstSymbol     -> print("CONST       ")
				is EnumSymbol      -> print("ENUM        ")
				is EnumEntrySymbol -> print("ENUM ENTRY  ")
				else               -> print("?           ")
			}

			if(symbol.scope.isNotEmpty) {
				print(symbol.scope)
				print('.')
			}

			println(symbol.name)
		}

		println()
	}


}