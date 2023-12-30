package eyre

fun main() {
	//Compiler.compile(Paths.get("samples"))
	EncParser("res/encs.txt").parse()
}