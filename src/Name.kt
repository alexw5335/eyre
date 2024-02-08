package eyre

import java.util.ArrayList
import java.util.HashMap


class Name(val id: Int, val string: String) {
	val isNull get() = id == 0
	val isNotNull get() = id != 0
	override fun equals(other: Any?) = this === other
	override fun hashCode() = id
	override fun toString() = string
	
	companion object {
		private var count = 0
		private val list = ArrayList<Name>()
		private val map = HashMap<String, Name>()
		operator fun get(id: Int) = list[id]
		operator fun get(key: String) = map.getOrPut(key) { Name(count++, key) }

		val STRING    = get("string")
		val NONE      = get("")
		val MAIN      = get("main")
		val VAR       = get("var")
		val NAMESPACE = get("namespace")
		val DLLCALL   = get("dllcall")
		val FUN       = get("fun")
		val STRUCT    = get("struct")
		val UNION     = get("union")
		val ENUM      = get("enum")
		val PROC      = get("proc")
		val TYPEDEF   = get("typedef")
		val CONST     = get("const")
		val BYTE      = get("byte")
		val WORD      = get("word")
		val DWORD     = get("dword")
		val QWORD     = get("qword")
		val COUNT     = get("count")
		val SIZE      = get("size")
		val widths    = Width.entries.associateBy { get(it.string) }
		val regs      = Reg.entries.associateBy { get(it.string) }
		val mnemonics = Mnemonic.entries.associateBy { get(it.string) }
	}
}