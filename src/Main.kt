package eyre

import java.nio.file.Paths

fun main() {
	Compiler.compileDir(Paths.get("samples"))
}