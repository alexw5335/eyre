package eyre

import java.nio.file.Path

class Context(val buildDir: Path, val files: List<SrcFile>) {


	val errors = ArrayList<EyreError>()

	val strings = ArrayList<String>()

	val symTable = SymbolTable()

	val linkRelocs = ArrayList<Reloc>()

	val absRelocs = ArrayList<Reloc>()

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



	// Errors



	fun internalErr(message: String? = "no reason given"): Nothing =
		error("Internal compiler error: $message")

	fun err(message: String, srcPos: SrcPos? = null): Nothing =
		throw EyreError(srcPos, message).also(errors::add)



	// Names



	private fun appendQualifiedName(builder: StringBuilder, sym: Symbol) {
		if(sym.parent !is RootSym) {
			appendQualifiedName(builder, sym.parent)
			builder.append('.')
		}
		builder.append(sym.name)
	}

	fun qualifiedName(sym: Symbol) = buildString { appendQualifiedName(this, sym) }

}