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

	fun compile() {
		try {
			compileInternal()
		} catch(e: Exception) {
			System.err.println("An unhandled internal compiler exception occurred:\n")
			e.printStackTrace()
			System.err.println("\nHandled compiler errors:\n")
			context.errors.forEach(::printError)
		}
	}



	private fun compileInternal() {
		context.buildDir.createDirectories()
		Files
			.list(context.buildDir)
			.toList()
			.filter { !it.isDirectory() }
			.forEach { it.deleteIfExists() }

		Lexer(context).lex()
		if(checkErrors(EyreStage.LEX))
			exitProcess(1)

		Parser(context).parse()
		if(checkErrors(EyreStage.PARSE))
			exitProcess(1)

		Resolver(context).resolve()
		if(checkErrors(EyreStage.RESOLVE))
			exitProcess(1)

		Assembler(context).assemble()
		if(checkErrors(EyreStage.ASSEMBLE))
			exitProcess(1)

		checkOutput(EyreStage.ASSEMBLE)
	}



	private fun printError(error: EyreError) {
		if(error.srcPos != null)
			System.err.println("${error.srcPos.file.relPath}:${error.srcPos.line} -- ${error.message}")
		else
			System.err.println(error.message)

		for(s in error.stackTrace)
			if("err" !in s.methodName && "Err" !in s.methodName)
				System.err.println("\t$s")

		System.err.println()
	}



	private fun checkErrors(stage: EyreStage): Boolean {
		if(context.errors.isEmpty())
			return false
		checkOutput(stage)
		context.errors.forEach(::printError)
		System.err.println("Compiler encountered errors")
		return true
	}



	private fun checkOutput(stage: EyreStage) {
		val printer = Printer(context)
		if(stage >= EyreStage.LEX)
			printer.writeTokens()
		if(stage >= EyreStage.PARSE)
			printer.writeNodes()
		if(stage >= EyreStage.PARSE)
			printer.writeSymbols()
		if(stage >= EyreStage.ASSEMBLE)
			printer.writeDisasm()
	}



}