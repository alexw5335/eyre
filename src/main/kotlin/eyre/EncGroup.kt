package eyre

import eyre.gen.Ops
import eyre.gen.SseOps
import eyre.util.bin8888

class EncGroup(val mnemonic: Mnemonic) {


	var isSse = false

	val encs = ArrayList<Enc>()

	var ops = 0L

	fun add(enc: Enc) {
		if(enc.sseOps != SseOps.NULL) {
			isSse = true
			if(encs.any { it.sseOps == SseOps.NULL })
				error("Mixed SSE and GP encodings: $mnemonic")
			encs += enc
		} else if(enc.ops in this) {
			//encs[enc.ops.index] = enc
		} else {
			ops = ops or (1L shl enc.ops.ordinal)
			encs += enc
		}
	}

	private val Ops.index get() = (ops and ((1L shl ordinal) - 1)).countOneBits()

	operator fun get(ops: Ops) = encs[ops.index]
	operator fun contains(operands: Ops) = this.ops and (1L shl operands.ordinal) != 0L
	operator fun get(ops: SseOps) = encs.first { it.sseOps == ops }
	override fun toString() = "Group($mnemonic, ${ops.toInt().bin8888}, ${encs.joinToString()})"

}