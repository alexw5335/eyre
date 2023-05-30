package eyre.nasm

import eyre.Width
import eyre.util.hexc8

class NasmEncoding(
	val line      : NasmLine,
	val mnemonic  : String,
	val opcodeExt : Int,
	val opcode    : Int,
	val oplen     : Int,
	val prefix    : Int,
	val rexw      : Boolean,
	val o16       : Boolean,
	val a32       : Boolean,
	val operands  : NasmOps,
	val width     : Width?,
	val ops       : List<NasmOp>
) {

	override fun toString() = buildString {
		if(prefix != 0) append("${prefix.hexc8} ")
		for(i in 0 until oplen) {
			val value = (opcode shr (i shl 3)) and 0xFF
			append(value.hexc8)
			append(' ')
		}
		deleteAt(length - 1)
		if(opcodeExt >= 0) {
			append('/')
			append(opcodeExt)
		}
		append("  ")
		append(mnemonic)
		append("  ")
		append(operands)
	}

}