package eyre

import java.nio.file.Path

class Context(val buildDir: Path, val files: List<SrcFile>) {


	val errors = ArrayList<EyreError>()

	val symTable = SymTable()

	val dllImports = HashMap<Name, DllImport>()

	val textWriter = BinWriter()

	val dataWriter = BinWriter()

	val rdataWriter = BinWriter()

	val linkWriter = BinWriter()

	val bssSize = 0

	val sections = ArrayList<Section>()

	val textSec  = Section(0, ".text", 0x60_00_00_20U).also(sections::add)

	val dataSec  = Section(1, ".data", 0xC0_00_00_40U).also(sections::add)

	val rdataSec = Section(2, ".rdata", 0x40_00_00_40U).also(sections::add)

	val bssSec   = Section(3, ".bss", 0xC0_00_00_80U).also(sections::add)

	val linkRelocs = ArrayList<Reloc>()

	val absRelocs = ArrayList<Reloc>()

	var entryPoint: PosSym? = null



	// Errors



	fun internalErr(message: String? = "no reason given"): Nothing =
		error("Internal compiler error: $message")

	fun err(srcPos: SrcPos?, message: String): Nothing {
		val error = EyreError(srcPos, message)
		errors.add(error)
		throw error
	}



	// Names



	private fun appendQualifiedName(builder: StringBuilder, sym: Sym) {
		sym.parent?.let {
			appendQualifiedName(builder, it)
			builder.append('.')
		}
		builder.append(sym.name)
	}

	fun qualifiedName(sym: Sym) = buildString { appendQualifiedName(this, sym) }



	// misc.



	fun getDllImport(dllName: Name, name: Name): DllImportSym {
		if(dllName == Name.NONE)
			err(null, "Not yet implemented")
		return dllImports
			.getOrPut(dllName) { DllImport(dllName) }
			.imports.getOrPut(name) { DllImportSym(name) }
	}


}