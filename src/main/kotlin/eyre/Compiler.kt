package eyre

import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Token and node lines aren't working properly
 */
class Compiler(private val context: CompilerContext) {


	private fun SymbolTable.addDefaultSymbols() {
		add(ByteType)
		add(WordType)
		add(DwordType)
		add(QwordType)
	}



	fun compile() {
		context.symbols.addDefaultSymbols()
		val lexer = Lexer()
		val parser = Parser(context)

		for(srcFile in context.srcFiles) {
			lexer.lex(srcFile)
			parser.parse(srcFile)
			printNodes(srcFile)
		}

		Resolver(context).resolve()

		printSymbols()
	}



	/*
	Binary
	 */



	private fun run(vararg params: String) {
		val process = Runtime.getRuntime().exec(params)
		val reader = BufferedReader(InputStreamReader(process.inputStream))

		while(true) {
			val line = reader.readLine()
			println(line ?: break)
		}

		process.errorReader().readText().let {
			if(it.isNotEmpty()) {
				print("\u001B[31m$it\\u001B[0m")
				error("Process failed")
			}
		}

		process.waitFor()
	}




	private fun dumpbin() {
		printHeader("DUMPBIN")
		run("dumpbin", "/ALL", "test.exe")
	}



	private fun disassemble() {
		val sectionPos = context.sections[Section.TEXT]!!.pos

		printHeader("DISASSEMBLY")

		for(symbol in context.symbols.getAll()) {
			if(symbol !is ProcSymbol) continue

			val pos = sectionPos + symbol.pos
			val size = symbol.size

			println()
			printHeader("${symbol.qualifiedName} ($pos, $size)")
			Files.write(Paths.get("test.bin"), context.linkWriter.getTrimmedBytes(pos, size))
			run("ndisasm", "-b64", "test.bin")
		}

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



	private fun printNodes(srcFile: SrcFile) {
		printHeader("NODES (${srcFile.relPath}):")
		for(node in srcFile.nodes)
			println(node.printString)
		println()
	}



	private fun printSymbols() {
		printHeader("SYMBOLS")
		for(symbol in context.symbols.getAll()) {
			print(symbol::class.simpleName)
			print(' ')
			println(symbol.qualifiedName)
		}
		println()
	}




}