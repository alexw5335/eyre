package eyre

class Linker(private val context: CompilerContext) {


	private val writer = context.linkWriter

	private val sections = context.sections

	private var numSections = 0

	private var nextSectionRva = 0

	private var nextSectionPos = 0

	private val Section.data get() = sections[this] ?: error("Invalid section: $this")



	private fun writeHeaders() {
		writer.i16(0x5A4D)
		writer.seek(0x3C)
		writer.i32(0x40)

		writer.i32(0x4550)     // signature
		writer.i16(0x8664)     // machine
		writer.i16(1)          // numSections    (fill in later)
		writer.i32(0)          // timeDateStamp
		writer.i32(0)          // pSymbolTable
		writer.i32(0)          // numSymbols
		writer.i16(0xF0)       // optionalHeaderSize
		writer.i16(0x0022)     // characteristics, DYNAMIC_BASE | LARGE_ADDRESS_AWARE | EXECUTABLE

		writer.i16(0x20B)      // magic
		writer.i16(0)          // linkerVersion
		writer.i32(0)          // sizeOfCode
		writer.i32(0)          // sizeOfInitialisedData
		writer.i32(0)          // sizeOfUninitialisedData
		writer.i32(0)          // pEntryPoint    (fill in later)
		writer.i32(0)          // baseOfCode
		writer.i64(0x400000)   // imageBase
		writer.i32(0x1000)     // sectionAlignment
		writer.i32(0x200)      // fileAlignment
		writer.i16(6)          // majorOSVersion
		writer.i16(0)          // minorOSVersion
		writer.i32(0)          // imageVersion
		writer.i16(6)          // majorSubsystemVersion
		writer.i16(0)          // minorSubsystemVersion
		writer.i32(0)          // win32VersionValue
		writer.i32(0)          // sizeOfImage    (fill in later)
		writer.i32(0x200)      // sizeOfHeaders
		writer.i32(0)          // checksum
		writer.i16(3)          // subsystem
		writer.i16(0x140)      // dllCharacteristics
		writer.i64(0x100000)   // stackReserve
		writer.i64(0x1000)     // stackCommit
		writer.i64(0x100000)   // heapReserve
		writer.i64(0x1000)     // heapCommit
		writer.i32(0)          // loaderFlags
		writer.i32(16)         // numDataDirectories
		writer.advance(16 * 8) // dataDirectories (fill in later)
		writer.seek(0x200)     // section headers (fill in later)

		nextSectionPos = fileAlignment
		nextSectionRva = sectionAlignment
	}



	private fun writeSection(
		section   : Section,
		name      : String,
		flags     : Int,
		size      : Int,
		extraSize : Int,
	) {
		val rawDataSize = size.roundToFile  // Must be aligned to fileAlignment
		val virtualSize = size + extraSize  // No alignment requirement
		val rawDataPos = if(size != 0) nextSectionPos else 0 // Must be aligned to fileAlignment
		val virtualAddress = nextSectionRva // Must be aligned to sectionAlignment

		nextSectionPos += rawDataSize
		nextSectionRva += virtualSize.roundToSection

		writer.seek(sectionHeadersPos + numSections++ * 40)
		writer.ascii64(name)
		writer.i32(virtualSize)
		writer.i32(virtualAddress)
		writer.i32(rawDataSize)
		writer.i32(rawDataPos)
		writer.zero(12)
		writer.i32(flags)
		writer.seek(nextSectionPos)

		sections[section] = SectionData(virtualSize, virtualAddress, rawDataPos)
	}



	private fun writeSections() {
		if(context.textWriter.isNotEmpty) {
			writer.bytes(context.textWriter)
			writeSection(Section.TEXT, ".text", 0x60000020, context.textWriter.pos, 0)
		}

		if(context.dataWriter.isNotEmpty) {
			writer.bytes(context.dataWriter)
			writeSection(Section.DATA, ".data", 0xC0000040L.toInt(), context.dataWriter.pos, 0)
		}

		if(context.bssSize > 0) {
			writeSection(Section.BSS, ".bss", 0xC0000080L.toInt(), 0, context.bssSize)
		}
	}



	fun link() {
		writeHeaders()
		writeSections()
		writeImports()
		writeSymbolTable()
		val finalSize = writer.pos
		context.relocs.forEach { it.writeRelocation() }

		if(context.entryPoint != null) {
			writer.seek(entryPointPos)
			writer.i32(context.entryPoint!!.address)
		}

		writer.seek(imageSizePos)
		writer.i32(nextSectionRva)

		writer.seek(numSectionsPos)
		writer.i32(numSections)

		writer.seek(finalSize)
	}



	private fun writeSymbolTable() {
/*		if(context.debugLabels.isEmpty()) return
		val pos = nextSectionPos
		writer.seek(nextSectionPos)

		for(label in context.debugLabels) {
			val name = label.name.string
			if(name.length > 8) error("Debug names longer than 8 bytes not yet supported")
			if(name[0].isDigit()) error("Debug names cannot start with digits")
			writer.ascii64(name)
			writer.i32(label.pos)
			writer.i16(1)
			writer.i16(0)
			writer.i8(2)
			writer.i8(0)
		}

		writer.align(fileAlignment)
		writer.i32(symbolTablePosPos, pos)
		writer.i32(numSymbolsPos, context.debugLabels.size)*/
	}




	private fun writeImports() {
		val dlls = context.dllImports.values
		if(dlls.isEmpty()) return

		val idtsRva = nextSectionRva
		val idtsPos = writer.pos
		val idtsSize = dlls.size * 20 + 20
		val offset = idtsPos - idtsRva

		writer.i32(idataDirPos, idtsRva)
		writer.i32(idataDirPos + 4, dlls.size * 20 + 20)
		writer.zero(idtsSize)

		for((dllIndex, dll) in dlls.withIndex()) {
			val idtPos = idtsPos + dllIndex * 20
			val dllNamePos = writer.pos

			writer.ascii(dll.name.string)
			writer.ascii(".dll")
			writer.i8(0)
			writer.align8() // Not necessary?

			val iltPos = writer.pos

			writer.zero(dll.imports.size * 8 + 8)

			val iatPos = writer.pos

			writer.zero(dll.imports.size * 8 + 8)

			for((importIndex, import) in dll.imports.values.withIndex()) {
				writer.i32(iltPos + importIndex * 8, writer.pos - offset)
				writer.i32(iatPos + importIndex * 8, writer.pos - offset)
				writer.i16(0)
				writer.asciiNT(import.name.string)
				writer.alignEven()
				import.pos = iatPos + importIndex * 8 - idtsPos
			}

			writer.i32(idtPos, iltPos - offset)
			writer.i32(idtPos + 12, dllNamePos - offset)
			writer.i32(idtPos + 16, iatPos - offset)
		}

		writeSection(Section.IDATA, ".idata", 0x40000040, writer.pos - idtsPos, 0)
	}



	// Relocations



	private val PosSymbol.address get() = section.data.rva + pos



	private fun resolveImmRec(node: AstNode, regValid: Boolean): Long {
		if(node is IntNode) return node.value
		if(node is UnaryNode) return node.calculate(::resolveImmRec, regValid)
		if(node is BinaryNode) return node.calculate(::resolveImmRec, regValid)

		if(node is SymNode) {
			return when(val symbol = node.symbol ?: error("Unresolved symbol")) {
				is PosSymbol       -> symbol.address.toLong()
				is IntSymbol       -> symbol.intValue
				else               -> error("Invalid symbol: $symbol")
			}
		}

		error("Invalid imm node: $node")
	}



	private fun resolveImm(node: AstNode): Long {
		return resolveImmRec(node, true)
	}



	private fun Reloc.writeRelocation() {
		when(type) {
			RelocType.LINK -> {
				val value = resolveImm(node)
				writer.seek(section.data.pos + pos)
				writer.writeWidth(width, value)
			}

			RelocType.RIP -> {
				val value = resolveImm(node) - (section.data.rva + pos + width.bytes + offset)
				writer.seek(section.data.pos + pos)
				writer.writeWidth(width, value)
			}

			RelocType.ABS -> {
				error("Absolute relocations not yet supported")
			}
		}
	}


}



private const val sectionAlignment = 0x1000

private const val fileAlignment = 0x200

private const val numSectionsPos = 70

private const val entryPointPos = 104

private const val imageSizePos = 144

private const val idataDirPos = 208

private const val sectionHeadersPos = 328

private const val symbolTablePosPos = 76

private const val numSymbolsPos = 80

private val Int.roundToFile get() = (this + fileAlignment - 1) and -fileAlignment

private val Int.roundToSection get() = (this + sectionAlignment - 1) and -sectionAlignment