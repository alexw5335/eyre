package eyre

class BitList(array: BitArray) {


	constructor(initialCapacity: Int = 512) : this(BitArray(initialCapacity))

	var array = array; private set



	fun ensureCapacity(bitIndex: Int) {
		if(bitIndex < array.size) return
		array = array.copyOf(bitIndex * 2)
	}



	fun set(index: Int) {
		ensureCapacity(index)
		array.set(index)
	}



	operator fun get(index: Int) = array[index]


}