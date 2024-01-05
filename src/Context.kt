package eyre

import java.nio.file.Path

class Context(val buildDir: Path, val files: List<SrcFile>) {


	val errors = ArrayList<EyreError>()

	val strings = ArrayList<String>()

	val symTable = SymbolTable()



	fun qualifiedName(sym: Symbol) = buildString {
		fun rec(sym2: Symbol) {
			if(sym2.parent != 0) {
				rec(symTable.get(sym2.parent))
				append('.')
			}
			append(sym.name)
		}
		rec(sym)
	}



	fun internalErr(message: String? = "no reason given"): Nothing =
		error("Internal compiler error: $message")

	fun err(message: String, srcPos: SrcPos? = null): Nothing =
		throw EyreError(srcPos, message).also(errors::add)


}