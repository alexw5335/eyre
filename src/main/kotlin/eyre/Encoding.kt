package eyre

data class Encoding(
	val mnemonic : Mnemonic,
	val prefix   : Prefix,
	val escape   : Escape,
	val opcode   : Int,
	val mask     : OpMask,
	val ext      : Int,
	val ops      : Ops,
	val rexw     : Int,
	val o16      : Int,
	val norexw   : Int,
	val pseudo   : Int,
)