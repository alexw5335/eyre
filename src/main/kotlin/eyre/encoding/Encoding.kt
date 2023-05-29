package eyre.encoding

data class Encoding(
	val mnemonic  : String,
	val prefix    : Int,
	val opcode    : Int,
	val oplen     : Int,
	val extension : Int,
	val operands  : Ops,
	val widths    : Widths,
)