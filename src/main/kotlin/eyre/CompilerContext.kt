package eyre

import eyre.util.NativeWriter

class CompilerContext(val srcFiles: List<SrcFile>) {


	val symbols = SymbolTable()

	var entryPoint: PosSymbol? = null

	var textWriter = NativeWriter()

	var dataWriter = NativeWriter()

	var bssSize = 0

	val relocs = ArrayList<Reloc>()

	val linkWriter = NativeWriter()

	val sections = HashMap<Section, SectionData>()

	val parentMap = HashMap<Scope, Symbol>()

	fun addParent(parent: ScopedSymbol) = parentMap.put(parent.thisScope, parent)

	val typeNodes = ArrayList<AstNode>() // Nodes that are resolved in the second resolver stage



	/*
	Dlls
	 */



	val dllImports = HashMap<Name, DllImports>()

	private val dllDefs = HashMap<Name, DllDef>()



	fun loadDllDefFromResources(name: String) {
		val path = "/defs/$name.txt"
		val stream = this::class.java.getResourceAsStream(path)
			?: error("Could not load dll def: $path")
		val exports = stream.reader().readLines().map(Names::add).toSet()
		val nameIntern = Names.add(name)
		dllDefs[nameIntern] = DllDef(nameIntern, exports)
	}



	fun getDllImport(name: Name): DllImportSymbol? {
		for(dll in dllImports.values)
			dll.imports[name]?.let { return it }

		for(def in dllDefs.values) {
			if(name !in def.exports) continue

			return dllImports.getOrPut(def.name) {
				DllImports(def.name, HashMap())
			}.imports.getOrPut(name) {
				DllImportSymbol(SymBase(Scopes.EMPTY, name))
			}
		}

		return null
	}


}