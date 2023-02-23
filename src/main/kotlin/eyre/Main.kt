package eyre

import java.nio.file.Paths
import kotlin.io.path.relativeTo



fun main() {
	//gen()
	test()
}



private fun test() {
	Compiler("samples", "main.eyre").compile()
}



private fun gen() {
	EncodingGenerator(Paths.get("encodings.txt")).gen()
}



private fun Compiler(directory: String, vararg files: String): Compiler {
	val srcFiles = files.map {
		val root = Paths.get(directory)
		val path = root.resolve(it)
		val relPath = path.relativeTo(root)
		SrcFile(path, relPath)
	}

	return Compiler(CompilerContext(srcFiles))
}