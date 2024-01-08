package eyre

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*
import kotlin.system.exitProcess

class Compiler(private val context: Context) {


	companion object {
		fun compile(srcDir: Path) {
			val buildDir = Paths.get("build")

			val srcFiles = Files.walk(srcDir)
				.toList()
				.filter { it.extension == "eyre" }
				.mapIndexed { i, p -> SrcFile(i, p, p.relativeTo(srcDir)) }

			if(srcFiles.isEmpty())
				error("No source files found")

			val compiler = Compiler(Context(buildDir, srcFiles))
			compiler.compile()
		}
	}



	private fun checkErrors(): Boolean {
		if(context.errors.isNotEmpty()) {
			for(e in context.errors) {
				if(e.srcPos != null)
					System.err.println("${e.srcPos.file.relPath}:${e.srcPos.line} -- ${e.message}")
				for(s in e.stackTrace)
					if("err" !in s.methodName && "Err" !in s.methodName)
						System.err.println("\t$s")
				System.err.println()
			}
			System.err.println("Compiler encountered errors")
			return true
		}
		return false
	}



	fun compile() {
		context.buildDir.createDirectories()
		Files
			.list(context.buildDir)
			.toList()
			.filter { !it.isDirectory() }
			.forEach { it.deleteIfExists() }

		// Lexing
		val lexer = Lexer(context)
		for(s in context.files)
			if(!s.invalid)
				lexer.lex(s)
		Printer(context, EyreStage.LEX).writeTokens()
		checkErrors()

		// Parsing
		val parser = Parser(context)
		for(s in context.files)
			if(!s.invalid)
				parser.parse(s)
		if(checkErrors()) {
			Printer(context, EyreStage.PARSE).writeNodes()
			exitProcess(1)
		}

		// Resolving
		val resolver = Resolver(context)
		resolver.resolve()
		if(checkErrors()) {
			Printer(context, EyreStage.RESOLVE).writeNodes()
			exitProcess(1)
		}

		Printer(context, EyreStage.RESOLVE).writeNodes()
	}


}