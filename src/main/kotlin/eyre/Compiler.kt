package eyre

import eyre.util.Util
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.relativeTo
import kotlin.random.Random

/**
 * Token and node lines aren't working properly
 */
class Compiler(private val context: CompilerContext) {


	companion object {

		fun create(directory: String): Compiler {
			val files = Files.list(Paths.get(directory)).toList().map { it.fileName.toString() }
			return create(directory, files)
		}

		fun create(directory: String, files: List<String>): Compiler {
			val srcFiles = files.map {
				val root = Paths.get(directory)
				val path = root.resolve(it)
				val relPath = path.relativeTo(root)
				SrcFile(path, relPath)
			}

			val context = CompilerContext(srcFiles)
			context.loadDllDef("kernel32", DefaultDllDefs.kernel32)
			context.loadDllDef("user32", DefaultDllDefs.user32)
			context.loadDllDef("gdi32", DefaultDllDefs.gdi32)
			context.loadDllDef("msvcrt", DefaultDllDefs.msvcrt)

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
		val lexer = Lexer()
		val parser = Parser(context)

		for(srcFile in context.srcFiles) {
			lexer.lex(srcFile)
			parser.parse(srcFile)
			printNodes(srcFile)
		}

		for(srcFile in context.srcFiles) {
			Files.newBufferedWriter(Paths.get("tokens.txt")).use {
				DebugOutput.printTokens(it, srcFile)
			}
		}

		printSymbols()
		Resolver(context).resolve()
		//printResolution()

		Assembler(context).assemble()
		Linker(context).link()
		Files.write(Paths.get("test.exe"), context.linkWriter.getTrimmedBytes())
		disassemble()
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