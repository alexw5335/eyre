package eyre.util

object Unique {

	private val uniques = HashMap<String, HashSet<String>>()

	private val String.set get() = uniques.getOrPut(this, ::HashSet)

	operator fun set(key: String, value: String) = key.set.add(value)

	operator fun plus(value: String) = "misc".set.add(value)

	fun print(key: String, value: String, print: String) {
		val set = uniques.getOrPut(key, ::HashSet)
		if(value in set) return
		set += value
		println(print)
	}

	fun print(key: String, value: String) = print(key, value, value)

	fun print(value: String) = print("misc", value)

}