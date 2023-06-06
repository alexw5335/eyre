package eyre.encoding

import eyre.Escape
import eyre.OpMask
import eyre.Prefix

data class EncodingLine(
	val lineNumber : Int,
	val prefix     : Prefix,
	val escape     : Escape,
	val extension  : Int,
	val opcode     : Int,
	val oplen      : Int,
	val mnemonic   : String,
	val ops        : String,
	val mask       : OpMask,
	val rexw       : Boolean,
	val o16        : Boolean,
)