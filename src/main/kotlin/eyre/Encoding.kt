package eyre

/**
 *     Bits 00-15: opcode
 *     Bits 16-17: prefix
 *     Bits 18-19: escape
 *     Bits 20-23: extension
 *     Bits 24-31: opMask1
 *     Bits 32-39: opMask2
 *     Bits 40-41: oplen
 *     Bits 42-42: rex.w
 *     Bits 43-43: o16
 */
@JvmInline
value class Encoding(val value: Long) {

	constructor(
		opcode: Int,
		prefix: Int, 
		escape: Int, 
		extension: Int,
		opMask1: OpMask,
		opMask2: OpMask,
		oplen: Int, 
		rexw: Int, 
		o16: Int
	) : this(
		(opcode.toLong() shl 0) or 
		(prefix.toLong() shl 16) or
		(escape.toLong() shl 18) or
		(extension.toLong() shl 20) or 
		(opMask1.value.toLong() shl 24) or
		(opMask2.value.toLong() shl 32) or
		(oplen.toLong() shl 40) or 
		(rexw.toLong() shl 42) or
		(o16.toLong() shl 43)
	)

	val opcode    get() = ((value shr 0 ) and 0xFFFF).toInt()
	val prefix    get() = ((value shr 16) and 0b11  ).toInt()
	val escape    get() = ((value shr 18) and 0b11  ).toInt()
	val extension get() = ((value shr 20) and 0b1111).toInt()
	val opMask1   get() = ((value shr 24) and 0xFF  ).toInt().let(::OpMask)
	val opMask2   get() = ((value shr 32) and 0xFF  ).toInt().let(::OpMask)
	val oplen     get() = ((value shr 40) and 0b11  ).toInt()
	val rexw      get() = ((value shr 42) and 0b1   ).toInt()
	val o16       get() = ((value shr 43) and 0b1   ).toInt()

}