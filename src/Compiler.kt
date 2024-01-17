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



	private fun checkOutput(stage: EyreStage) {
		val printer = Printer(context, stage)

		printer.writeTokens()
		if(stage >= EyreStage.PARSE)
			printer.writeNodes()
		if(stage >= EyreStage.PARSE)
			printer.writeSymbols()
	}



	private fun checkErrors(stage: EyreStage): Boolean {
		if(context.errors.isEmpty())
			return false

		checkOutput(stage)

		for(e in context.errors) {
			if(e.srcPos != null)
				System.err.println("${e.srcPos.file.relPath}:${e.srcPos.line} -- ${e.message}")
			else
				System.err.println(e.message)

			for(s in e.stackTrace)
				if("err" !in s.methodName && "Err" !in s.methodName)
					System.err.println("\t$s")

			System.err.println()
		}

		System.err.println("Compiler encountered errors")
		return true
	}



	fun compile() {
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

		// Lexing
		val lexer = Lexer(context)
		for(s in context.files)
			if(!s.invalid)
				lexer.lex(s)
		if(checkErrors(EyreStage.LEX))
			exitProcess(1)

		// Parsing
		val parser = Parser(context)
		for(s in context.files)
			if(!s.invalid)
				parser.parse(s)
		if(checkErrors(EyreStage.PARSE))
			exitProcess(1)


		// Resolving
		val resolver = Resolver(context)
		resolver.resolve()
		if(checkErrors(EyreStage.RESOLVE))
			exitProcess(1)

		checkOutput(EyreStage.ASSEMBLE)
	}


}