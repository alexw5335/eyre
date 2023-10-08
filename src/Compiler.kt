package eyre

import eyre.util.Unsafe
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.*
import kotlin.system.exitProcess

/**
 * Token and node lines aren't working properly
 */
class Compiler(private val context: Context) {


	companion object {

		fun create(directory: String): Compiler {
			val root = Paths.get(directory)

			val srcFiles = Files.walk(Paths.get(directory))
				.toList()
				.filter { it.extension == "eyre" }
				.map { SrcFile(it, it.relativeTo(root)) }

			if(srcFiles.isEmpty())
				error("No source files found")

			return Compiler(Context(srcFiles, Paths.get("build")))
		}

		fun create(directory: String, files: List<String>): Compiler {
			val srcFiles = files.map {
				val root = Paths.get(directory)
				val path = root.resolve(it)
				val relPath = path.relativeTo(root)
				SrcFile(path, relPath)
			}

			return Compiler(Context(srcFiles, Paths.get("build")))
		}

	}



	private fun checkErrors(): Boolean {
		if(context.errors.isNotEmpty()) {
			for(e in context.errors) {
				e.printStackTrace()
				System.err.println()
			}
			System.err.println("Compiler encountered errors")
			return true
		}
		return false
	}



	fun compile() {
		val buildDir = context.buildDir
		buildDir.createDirectories()

/*		Files
			.list(buildDir)
			.toList()
			.filter { it.isDirectory() }
			.forEach { it.deleteIfExists() }*/

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
		if(checkErrors()) {
			NodePrinter(context, CompilerStage.LEX).print()
			exitProcess(1)
		}

		// Resolving
		val resolver = Resolver(context)
		resolver.resolve()
		if(checkErrors()) {
			NodePrinter(context, CompilerStage.PARSE).print()
			exitProcess(1)
		}

		// Assembling
		Assembler(context).assemble()
		if(checkErrors()) {
			NodePrinter(context, CompilerStage.RESOLVE).print()
			exitProcess(1)
		}

		// Linking
		Linker(context).link()
		if(checkErrors()) {
			NodePrinter(context, CompilerStage.ASSEMBLE).print()
			exitProcess(1)
		} else {
			NodePrinter(context, CompilerStage.LINK).print()
		}

		Files.write(buildDir.resolve("test.exe"), context.linkWriter.getTrimmedBytes())

/*		for(sym in context.symbols) {
			if(sym !is Proc) continue
			if(sym.section != Section.TEXT) context.internalError("Invalid")
			println(sym.qualifiedName)
			val data = Unsafe.malloc(sym.size)
			Unsafe.setBytes(data, context.textWriter.bytes, sym.pos, sym.size)
			Natives.disassembleAndPrint(data, sym.size, context.getAddr(Section.TEXT).toLong())
			Unsafe.free(data)
			println()
		}*/
	}


}