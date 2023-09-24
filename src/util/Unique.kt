package eyre.util

object Unique : Iterable<Unique.Holder> {

	data class Holder(val value: String, var count: Int) {
		override fun toString() = "$value $count"
	}

	val set = HashMap<String, Holder>()

	fun add(value: String) = set.getOrPut(value) { Holder(value, 0) }.count++

	fun print(value: Any) = print(value.toString())

	fun print(value: String, print: String = value) {
		if(value in set) return
		add(value)
		println(print)
	}

	override fun iterator() = set.values.iterator()

}