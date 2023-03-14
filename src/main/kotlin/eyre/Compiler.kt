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
			//printNodes(srcFile)
			//printNodeTree(srcFile)
		}

		//printSymbols()
		Resolver(context).resolve()
		Assembler(context).assemble()
		Linker(context).link()
		Files.write(Paths.get("test.exe"), context.linkWriter.getTrimmedBytes())
		// dumpbin()
		disassemble()
	}



	/*
	Binary
	 */



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
		val pos = context.sections[Section.TEXT]!!.pos
		val size = context.sections[Section.TEXT]!!.size
		val bytes = context.linkWriter.getTrimmedBytes(pos, size)
		Files.write(Paths.get("test.bin"), bytes)
		printHeader("DISASSEMBLY")
		run("ndisasm", "-b64", "test.exe", "-e", "$pos", "-k", "$size,100000")
		println()
	}



	/*
	Printing
	 */



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



	private fun printNodeTree(node: AstNode, prefix: String) {
		println("Line ${node.srcPos.line}    $prefix${node.printString}")
		for(n in node.getChildren())
			printNodeTree(n, "$prefix    ")
	}



	private fun printNodeTree(srcFile: SrcFile) {
		printHeader("NODE TREE (${srcFile.relPath}):")

		for(node in srcFile.nodes)
			printNodeTree(node, "")
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
				is DllImportSymbol -> print("DLL IMPORT  ")
				is VarSymbol       -> print("VAR         ")
				is ResSymbol       -> print("RES         ")
				is ConstSymbol     -> print("CONST       ")
				is EnumSymbol      -> print("ENUM        ")
				is EnumEntrySymbol -> print("ENUM ENTRY  ")
				is ProcSymbol      -> print("PROC        ")
				else               -> print("?           ")
			}

			println(symbol.qualifiedName)
		}

		println()
	}


}