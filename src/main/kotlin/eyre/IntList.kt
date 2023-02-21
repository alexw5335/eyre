package eyre

class IntList(array: IntArray) {

	constructor(initialCapacity: Int = 512) : this(IntArray(initialCapacity))

	var array = array; private set

	var size = 0

	private fun ensureCapacity(index: Int) {
		if(index >= array.size)
			array = array.copyOf(index shl 2)
	}

	private fun ensureCapacity() {
		if(size >= array.size)
			array = array.copyOf(size shl 2)
	}

	fun add(value: Int) {
		ensureCapacity()
		array[size++] = value
	}

	operator fun set(index: Int, value: Int) {
		ensureCapacity(index)
		array[index] = value
	}

	operator fun get(index: Int) = array[index]

	operator fun plusAssign(value: Int) = add(value)

	operator fun contains(value: Int) = array.contains(value)

	fun array() = array.copyOf(size)

}