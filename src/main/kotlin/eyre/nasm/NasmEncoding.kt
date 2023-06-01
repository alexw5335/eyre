package eyre.nasm

import eyre.util.hexc8

class NasmEncoding(
	val line      : NasmLine,
	val mnemonic  : String,
	val extension : Int,
	val opcode    : Int,
	val oplen     : Int,
	val prefix    : Int,
	val escape    : Int,
	val rexw      : Boolean,
	val o16       : Boolean,
	val a32       : Boolean,
	val operands  : List<NasmOp>
) {

	override fun toString() = buildString {
		val list = ArrayList<String>()
		if(o16) list += "O16"
		if(a32) list += "A32"
		if(prefix != 0) list += prefix.hexc8
		if(rexw) list += "RW"

		when(escape) {
			1 -> list += "0F"
			2 -> { list += "0F"; list += "38" }
			3 -> { list += "0F"; list += "3A" }
		}

		for(i in 0 until oplen)
			list += ((opcode shr (i shl 3)) and 0xFF).hexc8
		append(list.joinToString(" "))
		if(extension >= 0) append("/$extension")
		append("  $mnemonic  ")
		append(operands.joinToString("_"))
	}

}