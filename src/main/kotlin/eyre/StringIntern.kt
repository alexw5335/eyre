package eyre



interface Intern {
	val id: Int
}



class StringIntern(override val id: Int, val hash: Int, val string: String) : Intern {
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	override fun toString() = string
}



class Scope(override val id: Int, val hash: Int, val array: IntArray) : Intern {
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	override fun toString() = array.joinToString(transform = { StringInterner[it].string })
}



class InternRange<T>(private val range: IntRange, private val elements: Array<T>) {
	operator fun contains(intern: StringIntern) = intern.id in range
	operator fun get(intern: StringIntern) = elements[intern.id - range.first]
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

	private fun<T> createRange(elements: Array<T>, supplier: (T) -> String): InternRange<T> {
		val range = IntRange(count, count + elements.size)
		for(e in elements) add(supplier(e))
		return InternRange(range, elements)
	}

	operator fun get(key: String) = map[key]

	//val keywords     = createRange(Keyword.values(), Keyword::string)
	//val widths       = createRange(Width.values(), Width::string)
	//val varWidths    = createRange(Width.values(), Width::varString)
	//val registers    = createRange(Register.values(), Register::string)
	//val prefixes     = createRange(Prefix.values(), Prefix::string)
	//val mnemonics    = createRange(Mnemonic.values(), Mnemonic::string)

}



object ScopeInterner : Interner<IntArray, Scope>() {

	fun add(key: IntArray, hash: Int) = map[key] ?: addInternal(key, Scope(count++, hash, key))

	val GLOBAL = add(IntArray(0), 0)

}



object StringInterns {

	private val String.intern get() = StringInterner[this]

	val RES    = "res" .intern
	val RESB   = "resb".intern
	val NULL   = "null".intern
	val EMPTY  = "".intern
	val GLOBAL = "global".intern
	val MAIN   = "main".intern
	val ENDP   = "endp".intern
	val SIZEOF = "sizeof".intern
	val REL    = "rel".intern
	val INT    = "int".intern
	val VOID   = "void".intern
	val ABS    = "abs".intern
	val BYTE   = "byte".intern
	val WORD   = "word".intern
	val DWORD  = "dword".intern
	val QWORD  = "qword".intern
	val I8     = "i8".intern
	val I16    = "i16".intern
	val I32    = "i32".intern
	val I64    = "i64".intern

}