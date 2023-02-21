package eyre

class CompilerContext(val srcFiles: List<SrcFile>) {

	val symbols = SymbolTable()

	val dlls = ArrayList<DllSymbol>()

}