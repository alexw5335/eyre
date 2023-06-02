package eyre

data class Encoding(
	val mnemonic  : Mnemonic,
	val prefix    : Int,
	val escape    : Int,
	val opcode    : Int,
	val oplen     : Int,
	val extension : Int,
	val operands  : Ops,
	val mask      : OpMask,
	val mask2     : OpMask,
	val rexw      : Int,
	val o16       : Boolean,
) {

	fun addToOpcode(value: Int) = opcode or (value shl ((oplen - 1) shl 3))

}