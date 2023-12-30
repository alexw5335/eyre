package eyre

import java.util.ArrayList
import java.util.HashMap



abstract class Intern(val id: Int) {
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	val isNull get() = id == 0
	val isNotNull get() = id != 0
}



abstract class Interner<K, V : Intern> {
	private var count = 0
	private val list = ArrayList<V>()
	private val map = HashMap<K, V>()
	abstract val creator: (id: Int, key: K) -> V
	fun add(key: K): V = map.getOrPut(key) { creator(count++, key).also(list::add) }
	operator fun get(id: Int) = list[id]
	operator fun get(key: K) = add(key)
}



class Name(id: Int, val string: String) : Intern(id) {
	override fun toString() = string
	companion object : Interner<String, Name>() {
		override val creator = ::Name
	}
}