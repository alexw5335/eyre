package eyre

data class Encoding(
	val mnemonic  : Mnemonic,
	val prefix    : Prefix,
	val escape    : Escape,
	val opcode    : Int,
	val extension : Int,
	val operands  : Ops,
	val mask      : OpMask,
	val rexw      : Int,
	val o16       : Boolean
) {

	val oplen = if(opcode and 0xFF00 != 0) 2 else 1

}