package eyre.encoding

import eyre.Mnemonic
import eyre.OpMask



/**
 * - Bits 0-15: Opcode
 * - Bits 16-17: prefix
 * - Bits 18-19: escape
 * - Bits 20-23: extension
 * - Bits 24-31: opMask1
 * - Bits 32-39: opMask2
 * - Bits 40-40: rex.w
 * - Bits 41-41: rex.r
 * - Bits 42-42: o16
 * - Bits 43-43: a32
 */
@JvmInline
value class Encoding(private val value: Long) {

	constructor(
		opcode: Int, prefix: Int, escape: Int, extension: Int,
		opMask1: OpMask, opMask2: OpMask, rexw: Int, rexr: Int,
		o16: Int, a32: Int
	) : this(
		(opcode.toLong() shl 0) or (prefix.toLong() shl 16) or (escape.toLong() shl 18) or
		(extension.toLong() shl 20) or (opMask1.value.toLong() shl 24) or (opMask2.value.toLong() shl 32) or
		(rexw.toLong() shl 40) or (rexr.toLong() shl 41) or (o16.toLong() shl 42) or (a32.toLong() shl 43)
	)

	val opcode    get() = ((value shr 0 ) and 0xFFFF).toInt()
	val prefix    get() = ((value shr 16) and 0b11  ).toInt()
	val escape    get() = ((value shr 18) and 0b11  ).toInt()
	val extension get() = ((value shr 20) and 0b1111).toInt()
	val opMask1   get() = ((value shr 24) and 0xFF  ).toInt().let(::OpMask)
	val opMask2   get() = ((value shr 32) and 0xFF  ).toInt().let(::OpMask)
	val rexw      get() = ((value shr 40) and 0b1   ).toInt()
	val rexr      get() = ((value shr 41) and 0b1   ).toInt()
	val o16       get() = ((value shr 42) and 0b1   ).toInt()
	val a32       get() = ((value shr 43) and 0b1   ).toInt()
	val mismatch  get() = opMask2.value != 0

}



class EncodingGroup(
	val mnemonic  : Mnemonic,
	val operands  : Int,
	val specs     : Int,
	val encodings : List<Encoding>,
)



data class ParsedEncoding(
	val mnemonic  : Mnemonic,
	val prefix    : Int,
	val opcode    : Int,
	val oplen     : Int,
	val extension : Int,
	val operands  : Ops,
	val opMask    : OpMask,
	val opMask2   : OpMask,
	val rexw      : Boolean,
	val rexr      : Boolean,
	val o16       : Boolean,
	val a32       : Boolean
)