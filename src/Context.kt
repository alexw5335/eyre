package eyre

import eyre.util.NativeWriter
import java.nio.file.Path

class Context(val srcFiles: List<SrcFile>, val buildDir: Path) {


	var entryPoint: PosSym? = null

	var textWriter = NativeWriter()

	var dataWriter = NativeWriter()

	var rdataWriter = NativeWriter()

	val linkRelocs = ArrayList<Reloc>()

	val absRelocs = ArrayList<Reloc>()

	val linkWriter = NativeWriter()

	var bssSize = 0

	val symbols = SymbolTable()

	fun writer(sec: Section) = when(sec) {
		Section.TEXT  -> textWriter
		Section.DATA  -> dataWriter
		Section.RDATA -> rdataWriter
		else          -> error("Invalid section: $sec")
	}

	val debugDirectives = ArrayList<DebugDirective>()

	val secIndexes = IntArray(Section.entries.size) { -1 }
	fun getIndex(sec: Section) = secIndexes[sec.ordinal]
	fun setIndex(sec: Section, value: Int) { secIndexes[sec.ordinal] = value }

	// Virtual addresses of each section relative to image start
	val secAddresses = IntArray(Section.entries.size)
	fun getAddr(sec: Section) = secAddresses[sec.ordinal]
	fun setAddr(sec: Section, value: Int) { secAddresses[sec.ordinal] = value }

	// File positions of each section relative to image file start
	val secPositions = IntArray(Section.entries.size)
	fun getPos(sec: Section) = secPositions[sec.ordinal]
	fun setPos(sec: Section, value: Int) { secPositions[sec.ordinal] = value }

	fun getTotalPos(sym: PosSym) = getPos(sym.section) + sym.pos
	fun getTotalAddr(sym: PosSym) = getAddr(sym.section) + sym.pos

	val errors = ArrayList<EyreException>()

	val dllImports = HashMap<Name, DllImports>()

	val dllDefs = HashMap<Name, DllDef>()

	val unorderedNodes = ArrayList<Node>()



	init {
		loadDefaultDllDefs()
		symbols.add(ByteType)
		symbols.add(WordType)
		symbols.add(DwordType)
		symbols.add(QwordType)
	}



	fun getSymbolAddress(symbol: PosSym) = getAddr(symbol.section) + symbol.pos




	/*
	Errors
	 */



	fun internalError(message: String? = null): Nothing {
		if(message != null)
			error("Internal compiler error: $message")
		else
			error("Internal compiler error")
	}

	fun err(srcPos: SrcPos?, message: String): Nothing {
		val error = EyreException(srcPos, message)
		errors.add(error)
		throw error
	}



	/*
    Dlls
     */



	fun loadDefaultDllDefs() {
		loadDllDef("kernel32", DefaultDllDefs.kernel32)
		loadDllDef("user32", DefaultDllDefs.user32)
		loadDllDef("gdi32", DefaultDllDefs.gdi32)
		loadDllDef("msvcrt", DefaultDllDefs.msvcrt)
	}



	fun loadDllDef(dllName: String, names: Array<String>) {
		val def = DllDef(Names.add(dllName), names.map(Names::add).toSet())
		dllDefs[def.name] = def
	}



	fun loadDllDefFromResources(name: String) {
		val path = "/defs/$name.txt"
		val stream = this::class.java.getResourceAsStream(path)
			?: error("Could not load dll def: $path")
		val exports = stream.reader().readLines().map(Names::add).toSet()
		val nameIntern = Names.add(name)
		dllDefs[nameIntern] = DllDef(nameIntern, exports)
	}



	fun getDllImport(name: Name): DllImport? {
		for(dll in dllImports.values)
			dll.imports[name]?.let { return it }

		for(def in dllDefs.values) {
			if(name !in def.exports) continue

			return dllImports.getOrPut(def.name) {
				DllImports(def.name, HashMap())
			}.imports.getOrPut(name) {
				DllImport(name)
			}
		}

		return null
	}


}