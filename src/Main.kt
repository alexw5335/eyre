package eyre

import eyre.gen.EncGen

fun main() {
	val opcodes = EncGen.zeroOperandOpcodes
	for(e in opcodes) println("$e,")
	//Compiler.create("samples/a", listOf("main.eyre")).compile()
}