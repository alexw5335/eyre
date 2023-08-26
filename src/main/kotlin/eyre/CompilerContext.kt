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

	val linkRelocs = ArrayList<Reloc>()

	val absRelocs = ArrayList<Reloc>()

	val linkWriter = NativeWriter()

	val parentMap = HashMap<Scope, Symbol>()

	fun addParent(parent: ScopedSymbol) = parentMap.put(parent.thisScope, parent)

	val unorderedNodes = ArrayList<AstNode>()

	val stringLiterals = ArrayList<StringLiteralSymbol>()

	val stringLiteralMap = HashMap<String, StringLiteralSymbol>() // Only for short strings

	val debugDirectives = ArrayList<DebugDirective>()

	var bssSize = 0

	// Virtual addresses of each section relative to image start
	val secAddresses = IntArray(Section.entries.size)
	fun getAddr(sec: Section) = secAddresses[sec.ordinal]
	fun setAddr(sec: Section, value: Int) { secAddresses[sec.ordinal] = value }

	// File positions of each section relative to image file start
	val secPositions = IntArray(Section.entries.size)
	fun getPos(sec: Section) = secPositions[sec.ordinal]
	fun setPos(sec: Section, value: Int) { secPositions[sec.ordinal] = value }

	fun writer(sec: Section) = when(sec) {
		Section.TEXT  -> textWriter
		Section.DATA  -> dataWriter
		Section.RDATA -> rdataWriter
		else          -> error("Invalid section: $sec")
	}




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