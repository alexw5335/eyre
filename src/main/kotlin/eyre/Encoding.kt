package eyre

data class Encoding(
	val mnemonic  : Mnemonic,
	val prefix    : Int,
	val escape    : Int,
	val opcode    : Int,
	val oplen     : Int,
	val extension : Int,
	val operands  : Ops,
	val opMask    : OpMask,
	val opMask2   : OpMask,
	val rexw      : Boolean,
	val o16       : Boolean,
)