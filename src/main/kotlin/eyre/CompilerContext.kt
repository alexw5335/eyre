package eyre

class CompilerContext(val srcFiles: List<SrcFile>) {


	val symbols = SymbolTable()

	val errors = ArrayList<EyreError>()

	fun internalError(): Nothing = error("Internal compiler error")


}