package eyre

import java.util.Arrays

class BitList(private var fields: LongArray) {


	constructor(initialCapacity: Int = 512) : this(LongArray(initialCapacity shr 6))



	private fun check(fieldIndex: Int) {
		if(fieldIndex < fields.size) return
		fields = fields.copyOf(fieldIndex * 2)
	}



	fun set(index: Int) {
		val fieldIndex = index shr 6
		check(fieldIndex)
		val bitIndex = index and 63
		fields[fieldIndex] = fields[fieldIndex] or (1L shl bitIndex)
	}



	fun clear(index: Int) {
		val fieldIndex = index shr 6
		check(fieldIndex)
		val bitIndex = index and 63
		fields[fieldIndex] = fields[fieldIndex] and (1L shl bitIndex).inv()
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



	fun clear() = Arrays.fill(fields, 0)

	fun copy() = BitList(fields.copyOf())


}