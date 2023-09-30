package eyre

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.*
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

			return Compiler(CompilerContext(srcFiles, Paths.get("build")))
		}

		fun create(directory: String, files: List<String>): Compiler {
			val srcFiles = files.map {
				val root = Paths.get(directory)
				val path = root.resolve(it)
				val relPath = path.relativeTo(root)
				SrcFile(path, relPath)
			}

			return Compiler(CompilerContext(srcFiles, Paths.get("build")))
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
		val buildDir = context.buildDir
		buildDir.createDirectories()

		Files
			.list(buildDir)
			.toList()
			.filter { it.isDirectory() }
			.forEach { it.deleteIfExists() }

		// Lexing
		val lexer = Lexer(context)
		for(s in context.srcFiles)
			if(!s.invalid)
				lexer.lex(s)
		DebugOutput.writeTokens(context)

		// Parsing
		val parser = Parser(context)
		for(s in context.srcFiles)
			if(!s.invalid)
				parser.parse(s)
		//DebugOutput.writeNodes(context)
		checkErrors()

		// Resolving
		val resolver = Resolver(context)
		resolver.resolve()
		checkErrors()

		// Assembling
		Assembler(context).assemble()
		checkErrors()

		// Linking
		Linker(context).link()
		checkErrors()
		Files.write(buildDir.resolve("test.exe"), context.linkWriter.getTrimmedBytes())
		DebugOutput.disassemble(context)
		AstWriter(context).write()
		DebugOutput.writeSymbols(context)
	}


}