package eyre

import eyre.util.bin16
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
			// MOVQ
			if(encodings.any { it.sseOps == encoding.sseOps})
				return
/*			if(encoding.sseOps.op1.isM || encoding.sseOps.op2.isM) {
				for(e in encodings) {
					if(e.sseOps.isSimilar(encoding.sseOps)) {
						if(e.sseOps.op1.isM || e.sseOps.op2.isM) {
							println("$mnemonic ${encoding.sseOps} ${e.sseOps}")
						}
					}
				}
			}*/
		} else if(encoding.ops !in this) {
			ops = ops or (1L shl encoding.ops.ordinal)
		}

		encodings += encoding
	}

	operator fun get(ops: Ops) = encodings[(this.ops and ((1L shl ops.ordinal) - 1)).countOneBits()]
	operator fun contains(operands: Ops) = this.ops and (1L shl operands.ordinal) != 0L
	operator fun get(ops: SseOps) = encodings.first { it.sseOps == ops }
	override fun toString() = "Group($mnemonic, ${ops.toInt().bin8888}, ${encodings.joinToString()})"
	// CVTSI2SD  X_M64      X_M32
	// CVTSI2SS  X_M64      X_M32
	// PINSRB    X_M8_I8    X_MEM_I8
	// PINSRW    MM_M16_I8  MM_MEM_I8
	// PINSRW    X_M16_I8   X_MEM_I8
}