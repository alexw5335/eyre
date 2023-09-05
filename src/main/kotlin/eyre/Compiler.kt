package eyre

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
			return Compiler(context)
		}

	}



	fun compile() {
		val lexer = Lexer(context)
		val parser = Parser(context)

		for(s in context.srcFiles)
			if(!s.invalid)
				lexer.lex(s)

		DebugOutput.printTokens(context)

		for(s in context.srcFiles)
			if(!s.invalid)
				parser.parse(s)

		DebugOutput.printNodes(context)

		if(context.errors.isNotEmpty()) {
			for(e in context.errors)
				System.err.println("${e.srcPos} -- ${e.message}")
			System.err.println("\nCompiler encountered errors (${context.errors.size})")
			exitProcess(1)
		}
	}


}