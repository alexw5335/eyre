package eyre

data class Encoding(
	val mnemonic : Mnemonic,
	val prefix   : Prefix,
	val escape   : Escape,
	val opcode   : Int,
	val mask     : OpMask,
	val ext      : Int,
	val ops      : Ops,
	val sseOps   : SseOps,
	val rexw     : Int,
	val o16      : Int,
	val pseudo   : Int,
	val sseEnc   : SseEnc,
) {
	val extension = if(ext == -1) 0 else ext
}