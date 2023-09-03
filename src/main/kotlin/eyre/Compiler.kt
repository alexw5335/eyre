package eyre

import eyre.util.Util
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.relativeTo
import kotlin.system.exitProcess

/**
 * Token and node lines aren't working properly
 */
class Compiler(private val context: CompilerContext) {


	companion object {

		fun create(directory: String): Compiler {
			val root = Paths.get(directory)

			val srcFiles = Files.walk(Paths.get(directory))
				.toList()
				.filter { it.extension == "eyre" }
				.map { SrcFile(it, it.relativeTo(root)) }

			if(srcFiles.isEmpty())
				error("No source files found")

			val context = CompilerContext(srcFiles)
			context.loadDefaultDllDefs()
			return Compiler(context)
		}

		fun create(directory: String, files: List<String>): Compiler {
			val srcFiles = files.map {
				val root = Paths.get(directory)
				val path = root.resolve(it)
				val relPath = path.relativeTo(root)
				SrcFile(path, relPath)
			}

			val context = CompilerContext(srcFiles)
			context.loadDefaultDllDefs()
			return Compiler(context)
		}

	}



	private fun SymbolTable.addDefaultSymbols() {
		fun Type.add() = add(this)
		fun Type.alias(name: String) = add(TypedefSymbol(SymBase(Scopes.EMPTY, Names.add(name), true), this))

		ByteType.add()
		WordType.add()
		DwordType.add()
		QwordType.add()
		ByteType.alias("i8")
		WordType.alias("i16")
		DwordType.alias("i32")
		QwordType.alias("i64")
	}



	fun compile() {
		context.symbols.addDefaultSymbols()
		val lexer = Lexer(context)
		val parser = Parser(context)

		for(s in context.srcFiles)
			if(!s.invalid)
				lexer.lex(s)

		DebugOutput.printTokens(context)

		for(s in context.srcFiles)
			if(!s.invalid)
				parser.parse(s)

		if(context.errors.isNotEmpty()) {
			for(e in context.errors)
				System.err.println("${e.srcPos} -- ${e.message}")
			System.err.println("\nCompiler encountered errors (${context.errors.size})")
			exitProcess(1)
		}

		// Resolver(context).resolve()
		// Assembler(context).assemble()
		// Linker(context).link()
		// Files.write(Paths.get("test.exe"), context.linkWriter.getTrimmedBytes())
		// disassemble()
	}



	/*
	Binary
	 */



	private fun dumpbin() {
		printHeader("DUMPBIN")
		Util.run("dumpbin", "/ALL", "test.exe")
	}



	private fun disassemble() {
		printHeader("DISASSEMBLY")

		for(symbol in context.symbols) {
			if(symbol !is ProcSymbol || symbol.section != Section.TEXT) continue

			val pos = context.getPos(symbol.section) + symbol.pos

			println()
			printHeader("${symbol.qualifiedName} ($pos, ${symbol.size})")
			Files.write(Paths.get("test.bin"), context.linkWriter.getTrimmedBytes(pos, symbol.size))
			Util.run("ndisasm", "-b64", "test.bin")
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



	private fun printTokens(srcFile: SrcFile) {
		printHeader("TOKENS (${srcFile.relPath}):")
		for(token in srcFile.tokens)
			println(token.printString)
		println()
	}
	private fun printNodes(srcFile: SrcFile) {
		printHeader("NODES (${srcFile.relPath}):")
		for(node in srcFile.nodes)
			println(node.printString)
		println()
	}



	private fun printSymbols() {
		printHeader("SYMBOLS")
		for(symbol in context.symbols) {
			print(symbol::class.simpleName)
			print(' ')
			println(symbol.qualifiedName)
		}
		println()
	}



	private fun printResolution() {
		printHeader("RESOLUTION")
		for(symbol in context.symbols) {
			when(symbol) {
				is StructSymbol -> {
					println("struct ${symbol.name} {")
					for(m in symbol.members)
						println("\t${m.offset} ${m.size} ${m.type.printString} ${m.name}")
					println("\t${symbol.size}")
					println("}")
				}
				is VarResSymbol -> println("var ${symbol.name}: ${symbol.type.printString} (size = ${symbol.type.size})")
				is ConstSymbol -> println("const ${symbol.name} = ${symbol.intValue}")
			}
		}
		println()
	}


}