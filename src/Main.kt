package eyre

import java.nio.file.Paths

fun main() {
	Compiler.compile(Paths.get("samples"))
}