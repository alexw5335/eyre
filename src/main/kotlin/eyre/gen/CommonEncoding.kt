package eyre.gen

import eyre.Escape
import eyre.Op
import eyre.Prefix

data class CommonEncoding(
	val mnemonic : String,
	val prefix   : Prefix,
	val escape   : Escape,
	val opcode   : Int,
	val ext      : Int,
	val ops      : List<Op>,
	val rexw     : Int,
	val o16      : Int,
	val pseudo   : Int,
	val sseEnc   : OpEnc?
)