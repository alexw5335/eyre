package eyre.encoding

import eyre.Escape
import eyre.Prefix
import eyre.util.hexc8

class NasmEncoding(
	val line      : NasmLine,
	val mnemonic  : String,
	val extension : Int,
	val opcode    : Int,
	val oplen     : Int,
	val prefix    : Prefix,
	val escape    : Escape,
	val rexw      : Boolean,
	val o16       : Boolean,
	val a32       : Boolean,
	val operands  : List<NasmOp>
) {

	override fun toString() = buildString {
		val list = ArrayList<String>()
		if(o16) list += "O16"
		if(a32) list += "A32"
		if(prefix != Prefix.NONE) list += prefix.value.hexc8
		if(rexw) list += "RW"

		when(escape) {
			Escape.NONE -> { }
			Escape.E0F  -> list += "0F"
			Escape.E38  -> { list += "0F"; list += "38" }
			Escape.E3A  -> { list += "0F"; list += "3A" }
			Escape.E00  -> { list += "0F"; list += "00" }
		}

		for(i in 0 until oplen)
			list += ((opcode shr (i shl 3)) and 0xFF).hexc8
		append(list.joinToString(" "))
		if(extension >= 0) append("/$extension")
		append("  $mnemonic  ")
		append(operands.joinToString("_"))
	}

}