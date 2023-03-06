package eyre

import eyre.util.NativeWriter

class CompilerContext(val srcFiles: List<SrcFile>) {


	val symbols = SymbolTable()

	val dlls = HashMap<StringIntern, DllSymbol>()

	var entryPoint: PosSymbol? = null

	var textWriter = NativeWriter()

	var dataWriter = NativeWriter()

	var bssSize = 0

	val relocs = ArrayList<Reloc>()

	val linkWriter = NativeWriter()

	val sections = arrayOfNulls<SectionData>(Section.values().size)


}