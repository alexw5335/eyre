package eyre

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*
import kotlin.system.exitProcess

class Compiler(val context: Context) {


	companion object {
		fun createAtDir(dir: Path, buildPath: Path = Paths.get("build")): Compiler {
			val files = Files
				.walk(dir)
				.toList()
				.filter { it.extension == "eyre" }
				.map { ProjectSrcFile(it.relativeTo(dir).toString(), it) }
			return Compiler(Context(buildPath, files))
		}
	}



	/*
	Variables
	 */



	val printer = Printer(context)

	val lexer = Lexer(context)

	val parser = Parser(context)

	val resolver = Resolver(context)

	val assembler = Assembler(context)

	val linker = Linker(context)



	/*
	Compilation
	 */



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
		IntTypes.ALL.forEach(context.symTable::add)
		for(file in context.files) {
			lexer.lex(file)
			printer.appendTokens(file, lexer.tokens)
			if(!file.invalid) {
				parser.parse(file, lexer.tokens)
				printer.appendNodes(file)
			}
		}
		checkErrors()
		for(file in context.files)
			resolver.resolveFile(file)
		checkErrors()
		for(file in context.files) assembler.assemble(file)
		checkErrors()
		linker.link()
		checkErrors()
		writeOutput()
	}



	/*
	Errors
	 */



	private fun printError(error: EyreError) {
		if(error.srcPos != null)
			System.err.println("${error.srcPos.file.name}:${error.srcPos.line} -- ${error.message}")
		else
			System.err.println(error.message)

		for(s in error.stackTrace)
			if("err" !in s.methodName && "Err" !in s.methodName)
				System.err.println("\t$s")

		System.err.println()
	}



	private fun checkErrors() {
		if(context.errors.isEmpty())
			return
		writeOutput()
		context.errors.forEach(::printError)
		System.err.println("Compiler encountered errors")
		exitProcess(1)
	}



	private fun writeOutput() {
		context.buildDir.createDirectories()
		Files
			.list(context.buildDir)
			.toList()
			.filter { !it.isDirectory() }
			.forEach { it.deleteIfExists() }

		printer.write("tokens.txt", printer.tokensBuilder)
		printer.write("nodes.txt", printer.nodesBuilder)

		if(context.linkWriter.isNotEmpty && context.errors.isEmpty()) {
			val exePath = context.buildDir.resolve("a.exe")
			Files.write(exePath, context.linkWriter.copy())
			printer.printDisasm()
		}
	}


}