package eyre

fun main() {
	Compiler.create("samples/a", listOf("main.eyre")).compile()
}