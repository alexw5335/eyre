package eyre.util

import java.util.*

class BitArray(private val fields: LongArray, val size: Int) {


	constructor(size: Int = 512) : this(LongArray((size shr 6).coerceAtLeast(1)), size)

	fun copyOf(size: Int) = BitArray(fields.copyOf((size shr 6).coerceAtLeast(1)), size)

	fun copy() = copyOf(size)



	fun set(index: Int) {
		val fieldIndex = index shr 6
		val bitIndex = index and 63
		fields[fieldIndex] = fields[fieldIndex] or (1L shl bitIndex)
	}



	operator fun get(index: Int): Boolean {
		val field = fields[index shr 6]
		val bitIndex = index and 63
		return field and (1L shl bitIndex) != 0L
	}



	fun count(index: Int): Int {
		var count = 0
		val fieldIndex = index shr 6
		for(i in 0 ..< fieldIndex)
			count += fields[i].countOneBits()
		count += (fields[fieldIndex] and (1L shl index) - 1).countOneBits()
		return count
	}



	fun clear(index: Int) {
		val fieldIndex = index shr 6
		val bitIndex = index and 63
		fields[fieldIndex] = fields[fieldIndex] and (1L shl bitIndex).inv()
	}



	fun clear() = Arrays.fill(fields, 0)


}