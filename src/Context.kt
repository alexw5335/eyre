package eyre

import java.nio.file.Path

class Context(val srcFiles: List<SrcFile>, val buildDir: Path) {

	val symbols = SymTable()
	var entryPoint: PosSym? = null
	val errors = ArrayList<EyreError>()
	val linkRelocs = ArrayList<Reloc>()
	val absRelocs = ArrayList<Reloc>()
	val textWriter = BinWriter()
	val dataWriter = BinWriter()
	val rdataWriter = BinWriter()
	val linkWriter = BinWriter()
	val bssSize = 0
	val sections  = ArrayList<Section>()
	val textSec  = Section(0, ".text", 0x60_00_00_20U).also(sections::add)
	val dataSec  = Section(1, ".data", 0xC0_00_00_40U).also(sections::add)
	val rdataSec = Section(2, ".rdata", 0x40_00_00_40U).also(sections::add)
	val bssSec   = Section(3, ".bss", 0xC0_00_00_80U).also(sections::add)
	val dllImports = HashMap<Name, DllImports>()
	val dllDefs = HashMap<Name, DllDef>()



	init {
		loadDllDef("kernel32", DefaultDllDefs.kernel32)
		loadDllDef("user32", DefaultDllDefs.user32)
		loadDllDef("gdi32", DefaultDllDefs.gdi32)
		loadDllDef("msvcrt", DefaultDllDefs.msvcrt)
	}



	/*
	Errors
	 */



	fun internalErr(message: String? = "no reason given"): Nothing =
		error("Internal compiler error: $message")

	fun err(srcPos: SrcPos, message: String): Nothing =
		throw EyreError(srcPos, message).also(errors::add)



	/*
    Dlls
     */



	fun loadDllDef(dllName: String, names: Array<String>) {
		val def = DllDef(Name.add(dllName), names.map(Name::add).toSet())
		dllDefs[def.name] = def
	}

	fun loadDllDefFromResources(name: String) {
		val path = "/defs/$name.txt"
		val stream = this::class.java.getResourceAsStream(path)
			?: error("Could not load dll def: $path")
		val exports = stream.reader().readLines().map(Name::add).toSet()
		val nameIntern = Name.add(name)
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