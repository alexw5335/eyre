package eyre

import java.nio.file.Path

class Context(val buildDir: Path, val files: List<SrcFile>) {


	val errors = ArrayList<EyreError>()

	val strings = ArrayList<String>()

	val symTable = SymbolTable()



	// Errors



	fun internalErr(message: String? = "no reason given"): Nothing =
		error("Internal compiler error: $message")

	fun err(message: String, srcPos: SrcPos? = null): Nothing =
		throw EyreError(srcPos, message).also(errors::add)



	// Names



	fun appendQualifiedName(builder: StringBuilder, sym: Symbol) {
		if(sym.parent !is RootSym) {
			appendQualifiedName(builder, sym.parent)
			builder.append('.')
		}
		builder.append(sym.name)
	}

	fun qualifiedName(sym: Symbol) = buildString { appendQualifiedName(this, sym) }

}