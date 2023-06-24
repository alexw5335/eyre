package eyre

import eyre.gen.Ops
import eyre.gen.SseOps



/**
 *     Bits 00-15: opcode  16
 *     Bits 16-18: prefix  3
 *     Bits 19-20: escape  2
 *     Bits 21-23: ext     3  /0../7
 *     Bits 24-29: mask    6  BYTE WORD DWORD QWORD TWORD XWORD
 *     Bits 30-30: rex.w   1
 *     Bits 31-31: o16     1
 */
@JvmInline
value class GpEnc(val value: Int) {

	constructor(
		opcode : Int,
		prefix : Int,
		escape : Int,
		ext    : Int,
		mask   : OpMask,
		rexw   : Int,
		o16    : Int,
	) : this(
		(opcode shl 0) or
		(prefix shl 16) or
		(escape shl 19) or
		(ext shl 21) or
		(mask.value shl 24) or
		(rexw shl 30) or
		(o16 shl 31)
	)

	val opcode  get() = ((value shr 0 ) and 0xFFFF)
	val prefix  get() = ((value shr 16) and 0b11)
	val escape  get() = ((value shr 19) and 0b11)
	val ext     get() = ((value shr 21) and 0x1111)
	val mask    get() = ((value shr 24) and 0b111111).let(::OpMask)
	val rexw    get() = ((value shr 30) and 0b1)
	val o16     get() = ((value shr 31) and 0b1)
	
}



/**
 *     Bits 00-07: opcode  8
 *     Bits 08-10: prefix  3
 *     Bits 11-12: escape  2
 *     Bits 13-15: ext     3  /0../7
 *     Bits 16-23: ops     8
 *     Bits 24-24: rex.w   1
 *     Bits 25-25: o16     1
 */
/*@JvmInline
value class SseEnc(val value: Int) {

	constructor(
		opcode : Int,
		prefix : Int,
		escape : Int,
		ext    : Int,
		ops    : SseOps,
		rexw   : Int,
		o16    : Int,
		mr     : Int,
	) : this(
		(opcode shl 0) or
			(prefix shl 16) or
			(escape shl 19) or
			(ext shl 21) or
			(mask.value shl 24) or
			(rexw shl 30) or
			(mr shl 31)
	)

	val opcode  get() = ((value shr 0 ) and 0xFF)
	val prefix  get() = ((value shr 16) and 0b11)
	val escape  get() = ((value shr 19) and 0b11)
	val ext     get() = ((value shr 21) and 0x1111)
	val mask    get() = ((value shr 24) and 0b111111).let(::OpMask)
	val rexw    get() = ((value shr 30) and 0b1)
	val mr      get() = ((value shr 31) and 0b1)

}*/



data class Enc(
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
	val mr       : Boolean
) {
	val extension = if(ext == -1) 0 else ext
}