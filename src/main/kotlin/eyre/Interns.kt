package eyre

import kotlin.collections.ArrayList
import kotlin.collections.HashMap


interface Intern {
	val id: Int
	val isEmpty get() = id == 0
}



class InternRange<T>(private val range: IntRange, private val elements: Array<T>) {
	operator fun contains(intern: Name) = intern.id in range
	operator fun get(intern: Name) = elements[intern.id - range.first]
}



class Name(override val id: Int, val hash: Int, val string: String) : Intern {
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	override fun toString() = string
}



class Scope(override val id: Int, val hash: Int, val array: IntArray) : Intern {
	val last get() = Names[array.last()]
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	override fun toString() = array.joinToString(transform = { Names[it].string }, separator = ".")
}



abstract class Interner<K, V : Intern> {

	protected var count = 0

	private val list = ArrayList<V>()

	protected val map = HashMap<K, V>()

	protected fun addInternal(key: K, value: V): V {
		list += value
		map[key] = value
		return value
	}

	operator fun get(id: Int) = list[id]

}



object Names : Interner<String, Name>() {

	fun add(key: String) = map[key] ?: addInternal(key, Name(count++, key.hashCode(), key))

	operator fun get(key: String) = map[key] ?: addInternal(key, Name(count++, key.hashCode(), key))

	private fun<T> createRange(elements: Array<T>, supplier: (T) -> String): InternRange<T> {
		val range = IntRange(count, count + elements.size - 1)
		for(e in elements) add(supplier(e))
		return InternRange(range, elements)
	}

	val EMPTY  = add("")
	val RES    = add("res")
	val MAIN   = add("main")
	val FS     = add("fs")
	val GS     = add("gs")
	val DEBUG  = add("debug")
	val NULL   = add("null")
	val VOID   = add("void")
	val SIZE   = add("size")
	val COUNT  = add("count")

	val keywords     = createRange(Keyword.values(), Keyword::string)
	val widths       = createRange(Width.values(), Width::string)
	val varWidths    = createRange(Width.values(), Width::varString)
	val registers    = createRange(Register.values(), Register::string)
	val prefixes     = createRange(Prefix.values(), Prefix::string)
	val mnemonics    = createRange(Mnemonic.values(), Mnemonic::string)
	val fpuRegisters = createRange(FpuReg.values(), FpuReg::string)

}



object Scopes : Interner<IntArray, Scope>() {

	fun add(key: IntArray, hash: Int) = map[key] ?: addInternal(key, Scope(count++, hash, key))

	fun add(key: IntArray) = add(key, key.contentHashCode())

	fun add(base: Scope, addition: Intern): Scope {
		val array = base.array.copyOf(base.array.size + 1)
		array[base.array.size] = addition.id
		return add(array)
	}

	fun add(base: Scope, addition: IntArray, size: Int): Scope {
		val array = base.array.copyOf(base.array.size + size)
		for(i in 0 until size) array[base.array.size + i] = addition[i]
		return add(array)
	}

	val EMPTY = add(IntArray(0), 0)

}