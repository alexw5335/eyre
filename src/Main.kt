package eyre

import java.nio.file.Paths

fun main() {
	Encs
	Compiler.compile(Paths.get("samples"))
}