package eyre

import java.nio.file.Paths

fun main() {
	val compiler = Compiler.createAtDir(Paths.get("samples"))
	compiler.compile()
}