package eyre

import eyre.util.NativeWriter
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class CompilerContext(val srcFiles: List<SrcFile>) {


	val symbols = SymbolTable()

	var entryPoint: PosSymbol? = null

	var textWriter = NativeWriter()

	var dataWriter = NativeWriter()

	var rdataWriter = NativeWriter()

	var bssSize = 0

	val linkRelocs = ArrayList<Reloc>()

	val absRelocs = ArrayList<Reloc>()

	val linkWriter = NativeWriter()

	val sections = Array<SectionData>(Section.entries.size) { SectionData(0, 0, 0) }

	val parentMap = HashMap<Scope, Symbol>()

	fun addParent(parent: ScopedSymbol) = parentMap.put(parent.thisScope, parent)

	val unorderedNodes = ArrayList<AstNode>()

	val stringLiterals = ArrayList<StringLiteralSymbol>()

	val stringLiteralMap = HashMap<String, StringLiteralSymbol>() // Only for short strings




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



	/*
	Strings
	 */



	fun addStringLiteral(string: String): StringLiteralSymbol {
		if(string.length <= 32) {
			stringLiteralMap[string]?.let { return it }
			val symbol = StringLiteralSymbol(string)
			stringLiterals.add(symbol)
			stringLiteralMap[string] = symbol
			return symbol
		} else {
			val symbol = StringLiteralSymbol(string)
			stringLiterals.add(symbol)
			return symbol
		}
	}

}