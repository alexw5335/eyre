package eyre.encoding

import eyre.OpMask

data class EncodingLine(
	val lineNumber : Int,
	val prefix     : Int,
	val escape     : Int,
	val extension  : Int,
	val opcode     : Int,
	val oplen      : Int,
	val mnemonic   : String,
	val ops        : String,
	val mask       : OpMask,
	val rexw       : Boolean,
	val o16        : Boolean,
)