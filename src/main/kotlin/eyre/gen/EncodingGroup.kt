package eyre.gen

import eyre.Mnemonic
import eyre.util.bin8888

class EncodingGroup(val mnemonic: Mnemonic) {


	var isSse = false

	val encodings = ArrayList<Encoding>()

	var ops = 0L

	fun add(encoding: Encoding) {
		if(encoding.sseOps != SseOps.NULL) {
			isSse = true
			if(encodings.any { it.sseOps == SseOps.NULL })
				error("Mixed SSE and GP encodings: $mnemonic")
			encodings += encoding
		} else if(encoding.ops !in this) {
			ops = ops or (1L shl encoding.ops.ordinal)
			encodings += encoding
		}
	}

	operator fun get(ops: Ops) = encodings[(this.ops and ((1L shl ops.ordinal) - 1)).countOneBits()]
	operator fun contains(operands: Ops) = this.ops and (1L shl operands.ordinal) != 0L
	operator fun get(ops: SseOps) = encodings.first { it.sseOps == ops }
	override fun toString() = "Group($mnemonic, ${ops.toInt().bin8888}, ${encodings.joinToString()})"

}