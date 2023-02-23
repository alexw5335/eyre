package eyre

import kotlin.collections.ArrayList
import kotlin.collections.HashMap



interface Intern {
	val id: Int
	val isEmpty get() = id == 0
	val isNotEmpty get() = id != 0
}



class InternRange<T>(private val range: IntRange, private val elements: Array<T>) {
	operator fun contains(intern: StringIntern) = intern.id in range
	operator fun get(intern: StringIntern) = elements[intern.id - range.first]
}



class StringIntern(override val id: Int, val hash: Int, val string: String) : Intern {
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	override fun toString() = string
}



class ScopeIntern(override val id: Int, val hash: Int, val array: IntArray) : Intern {
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	override fun toString() = array.joinToString(transform = { StringInterner[it].string }, separator = ".")
}



abstract class Interner<K, V : Intern> {

	protected var count = 0

	protected val list = ArrayList<V>()

	protected val map = HashMap<K, V>()

	protected fun addInternal(key: K, value: V): V {
		list += value
		map[key] = value
		return value
	}

	operator fun get(id: Int) = list[id]

}



object StringInterner : Interner<String, StringIntern>() {

	fun add(key: String) = map[key] ?: addInternal(key, StringIntern(count++, key.hashCode(), key))

	operator fun get(key: String) = map[key]

	private fun<T> createRange(elements: Array<T>, supplier: (T) -> String): InternRange<T> {
		val range = IntRange(count, count + elements.size)
		for(e in elements) add(supplier(e))
		return InternRange(range, elements)
	}

	val EMPTY  = add("")
	val RES    = add("res")
	val MAIN   = add("main")
	val SIZEOF = add("sizeof")
	val FS     = add("fs")
	val GS     = add("gs")

	val keywords     = createRange(Keyword.values(), Keyword::string)
	val widths       = createRange(Width.values(), Width::string)
	val varWidths    = createRange(Width.values(), Width::varString)
	val registers    = createRange(Register.values(), Register::string)
	val prefixes     = createRange(Prefix.values(), Prefix::string)
	val mnemonics    = createRange(Mnemonic.values(), Mnemonic::string)

}



object ScopeInterner : Interner<IntArray, ScopeIntern>() {

	fun add(key: IntArray, hash: Int) = map[key] ?: addInternal(key, ScopeIntern(count++, hash, key))

	val EMPTY = add(IntArray(0), 0)

}