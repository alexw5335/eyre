package eyre

import eyre.util.NativeWriter

class CompilerContext(val srcFiles: List<SrcFile>) {


	val symbols = SymbolTable()

	val constSymbols = ArrayList<Symbol>()

	val dlls = ArrayList<DllSymbol>()

	var entryPoint: PosSymbol? = null

	var textWriter = NativeWriter()

	var dataWriter = NativeWriter()

	var bssSize = 0

	val relocations = ArrayList<Relocation>()

	val linkWriter = NativeWriter()

	val sections = arrayOfNulls<SectionData>(Section.values().size)


}