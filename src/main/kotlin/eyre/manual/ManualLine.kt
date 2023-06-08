package eyre.manual

import eyre.*

data class ManualLine(
	val lineNumber : Int,
	val prefix     : Prefix,
	val escape     : Escape,
	val ext        : Int,
	val opcode     : Int,
	val mnemonic   : String,
	val opsString  : String,
	val mask       : OpMask,
	val rexw       : Int,
	val o16        : Int,
	val norexw     : Boolean,
	val pseudo     : Int,
) {
	val oplen = if(opcode and 0xFF00 != 0) 2 else 1
}