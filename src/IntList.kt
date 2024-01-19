package eyre

class IntList(var array: IntArray = IntArray(64), var size: Int = 0) {

	fun add(value: Int) {
		if(size >= array.size)
			array = IntArray(size * 2)
		array[size++] = value
	}

}