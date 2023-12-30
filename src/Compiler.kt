package eyre

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*

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
		context.buildDir.createDirectories()
		Files
			.list(context.buildDir)
			.toList()
			.filter { !it.isDirectory() }
			.forEach { it.deleteIfExists() }

		val printer = Printer(context)
		val lexer = Lexer(context)
		for(file in context.files) lexer.lex(file)
		printer.writeTokens()
		val parser = Parser(context)
		for(file in context.files) parser.parse(file)
		printer.writeNodes()
	}


}