package eyre

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*
import kotlin.system.exitProcess

class Compiler(private val context: Context) {


	companion object {

		fun compileFile(path: Path) {
			val srcFile = SrcFile(0, path, path.relativeTo(path))
			Compiler(Context(Paths.get("build"), listOf(srcFile))).compile()
		}

		fun compileDir(path: Path) {
			val srcFiles = Files.walk(path)
				.toList()
				.filter { it.extension == "eyre" }
				.mapIndexed { i, p -> SrcFile(i, p, p.relativeTo(path)) }
			if(srcFiles.isEmpty())
				error("No source files found")
			Compiler(Context(Paths.get("build"), srcFiles)).compile()
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

		context.symTable.add(Types.BYTE)
		context.symTable.add(Types.WORD)
		context.symTable.add(Types.DWORD)
		context.symTable.add(Types.QWORD)

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

		Linker(context).link()
		if(checkErrors(EyreStage.LINK))
			exitProcess(1)

		checkOutput(EyreStage.LINK)
		val exePath = context.buildDir.resolve("a.exe")
		Files.write(exePath, context.linkWriter.copy())
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