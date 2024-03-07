package eyre

import java.nio.file.Path

class Context(val buildDir: Path, val files: List<SrcFile>) {


	val errors = ArrayList<EyreError>()

	val symTable = SymTable()

	val dlls = HashMap<Name, Dll>()

	val textWriter = BinWriter()

	val dataWriter = BinWriter()

	val rdataWriter = BinWriter()

	val linkWriter = BinWriter()

	var bssSize = 0

	val sections = ArrayList<Section>()

	val textSec  = Section(0, ".text", 0x60_00_00_20U, textWriter).also(sections::add)

	val dataSec  = Section(1, ".data", 0xC0_00_00_40U, dataWriter).also(sections::add)

	val rdataSec = Section(2, ".rdata", 0x40_00_00_40U, rdataWriter).also(sections::add)

	val bssSec   = Section(3, ".bss", 0xC0_00_00_80U, null).also(sections::add)

	val linkRelocs = ArrayList<Reloc>()

	val absRelocs = ArrayList<AbsReloc>() // Only 64-bit immediate operands and 64-bit variables can produce these

	val ripRelocs = ArrayList<RelReloc>()

	var entryPoint: Pos? = null

	val stringLiterals = ArrayList<StringLitSym>()



	fun getDllImport(dllName: Name, name: Name): DllImport {
		return dlls
			.getOrPut(dllName) { Dll(dllName) }
			.imports
			.getOrPut(name) { DllImport(name, rdataSec, 0) }
	}

	fun err(srcPos: SrcPos?, message: String): Nothing {
		val error = EyreError(srcPos, message)
		errors.add(error)
		throw error
	}

	fun internalErr(message: String? = "no reason given"): Nothing =
		error("Internal compiler error: $message")

	private fun appendQualifiedName(builder: StringBuilder, sym: Sym) {
		sym.parent?.let {
			appendQualifiedName(builder, it)
			builder.append('.')
		}
		builder.append(sym.name)
	}

	fun qualifiedName(sym: Sym) = buildString { appendQualifiedName(this, sym) }



}