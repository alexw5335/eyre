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
		private val anonList = ArrayList<Name>()
		operator fun get(id: Int) = list[id]
		operator fun get(key: String) = map.getOrPut(key) { Name(count++, key) }

		fun anon(index: Int) = if(anonList.size <= index)
			get("${anonList.size}").also(anonList::add)
		else
			anonList[index]

		val NONE      = get("")
		val STRING    = get("string")
		val IF        = get("if")
		val ELIF      = get("elif")
		val ELSE      = get("else")
		val WHILE     = get("while")
		val FOR       = get("for")
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