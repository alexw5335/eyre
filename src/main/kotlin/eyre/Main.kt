package eyre

import eyre.encoding.EncodingReader
import eyre.nasm.NasmLine
import eyre.nasm.NasmReader
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.relativeTo



fun main() {
	Compiler("samples/testing2").compile()
}



private fun Compiler(directory: String): Compiler {
	val files = Files.list(Paths.get(directory)).toList().map { it.fileName.toString() }
	return Compiler(directory, files)
}



private fun Compiler(directory: String, files: List<String>): Compiler {
	val srcFiles = files.map {
		val root = Paths.get(directory)
		val path = root.resolve(it)
		val relPath = path.relativeTo(root)
		SrcFile(path, relPath)
	}

	val context = CompilerContext(srcFiles)
	context.loadDllDefFromResources("kernel32")
	context.loadDllDefFromResources("user32")
	context.loadDllDefFromResources("gdi32")
	context.loadDllDefFromResources("msvcrt")

	return Compiler(context)
}