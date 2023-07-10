package eyre

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.relativeTo


fun main() {
	Compiler("samples/testing2").compile()
}



private fun Compiler(directory: String): Compiler {
	val files = Files.list(Paths.get(directory)).toList().map { it.fileName.toString() }
	return Compiler.create(directory, files)
}