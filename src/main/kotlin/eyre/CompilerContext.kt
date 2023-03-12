package eyre

import eyre.util.NativeWriter
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readLines

class CompilerContext(val srcFiles: List<SrcFile>) {


	val symbols = SymbolTable()

	var entryPoint: PosSymbol? = null

	var textWriter = NativeWriter()

	var dataWriter = NativeWriter()

	var bssSize = 0

	val relocs = ArrayList<Reloc>()

	val linkWriter = NativeWriter()

	val sections = HashMap<Section, SectionData>()

	val debugLabels = ArrayList<DebugLabelSymbol>()



	/*
	Dlls
	 */



	val dllImports = HashMap<StringIntern, DllImports>()

	private val dllDefs = HashMap<StringIntern, DllDef>()



	fun loadDllDefFromResources(name: String) {
		val path = "/defs/$name.txt"
		val stream = this::class.java.getResourceAsStream(path)
			?: error("Could not load dll def: $path")
		val exports = stream.reader().readLines().map(StringInterner::add).toSet()
		val nameIntern = StringInterner.add(name)
		dllDefs[nameIntern] = DllDef(nameIntern, exports)
	}



	fun getDllImport(name: StringIntern): DllImportSymbol? {
		for(dll in dllImports.values)
			dll.imports[name]?.let { return it }

		for(def in dllDefs.values) {
			if(name !in def.exports) continue

			return dllImports.getOrPut(def.name) {
				DllImports(def.name, HashMap())
			}.imports.getOrPut(name) {
				DllImportSymbol(SymBase(null, ScopeInterner.EMPTY, name))
			}
		}

		return null
	}


}