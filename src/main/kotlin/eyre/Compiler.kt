package eyre

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
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

			return Compiler(CompilerContext(srcFiles))
		}

		fun create(directory: String, files: List<String>): Compiler {
			val srcFiles = files.map {
				val root = Paths.get(directory)
				val path = root.resolve(it)
				val relPath = path.relativeTo(root)
				SrcFile(path, relPath)
			}

			return Compiler(CompilerContext(srcFiles))
		}

	}



	private fun checkErrors() {
		if(context.errors.isNotEmpty()) {
			for(e in context.errors) {
				//System.err.println("${e.srcPos} -- ${e.message}")
				e.printStackTrace()
				System.err.println()
			}
			System.err.println("Compiler encountered errors")
			exitProcess(1)
		}
	}



	fun compile() {
		// Lexing
		val lexer = Lexer(context)
		for(s in context.srcFiles)
			if(!s.invalid)
				lexer.lex(s)
		DebugOutput.printTokens(context)

		// Parsing
		val parser = Parser(context)
		for(s in context.srcFiles)
			if(!s.invalid)
				parser.parse(s)
		DebugOutput.printNodes(context)
		checkErrors()

		// Resolving
		Resolver(context).resolve()
		checkErrors()

		// Assembling
		Assembler(context).assemble()
		checkErrors()

		// Linking
		Linker(context).link()
		checkErrors()
		val buildDir = Paths.get("build")
		buildDir.createDirectories()
		Files.write(buildDir.resolve("test.exe"), context.linkWriter.getTrimmedBytes())
		DebugOutput.disassemble(context)
		DebugOutput.printSymbols(context)
	}


}