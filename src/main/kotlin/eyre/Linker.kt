package eyre

import eyre.util.IntList

class Linker(private val context: CompilerContext) {


	private val writer = context.linkWriter

	private val sections = context.sections

	private var numSections = 0

	private var nextSectionRva = 0

	private var nextSectionPos = 0

	private val Section.data get() = sections[ordinal].also { if(it.rva == 0) error("Section not found: $this") }



	fun link() {
		writeHeaders()

		if(context.bssSize > 0) {
			writeSection(Section.BSS, ".bss", 0xC0000080L.toInt(), 0, context.bssSize)
		}

		writer.bytes(context.textWriter)
		writeSection(Section.TEXT, ".text", 0x60000020, context.textWriter.pos, 0)

		if(context.dataWriter.isNotEmpty) {
			writer.bytes(context.dataWriter)
			writeSection(Section.DATA, ".data", 0xC0000040L.toInt(), context.dataWriter.pos, 0)
		}

		sections[Section.RDATA.ordinal].rva = nextSectionRva

		writeImports()

		writeAbsRelocs()

		if(context.rdataWriter.isNotEmpty) {
			writer.bytes(context.rdataWriter)
			writeSection(Section.RDATA, ".rdata", 0x40_00_00_40, context.rdataWriter.pos, 0)
		}

		writeSymbolTable()

		val finalSize = writer.pos

		for(reloc in context.linkRelocs)
			reloc.writeRelocation()

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



	private fun writeAbsRelocs() {
		for(reloc in context.absRelocs) {
			val value = resolveImm(reloc.node)
			val rva = reloc.sec.data.rva + reloc.pos
			val pageRva = (rva shr 12) shl 12
			pages.getOrPut(pageRva) { IntList() }.add(rva - pageRva)
			writer.i64(reloc.sec.data.pos + reloc.pos, value + imageBase)
		}

		if(pages.isEmpty()) return

		val writer = context.rdataWriter

		val startPos = writer.pos
		val startRva = writer.pos + Section.RDATA.data.rva

		writer.pos = startPos

		for((rva, offsets) in pages) {
			writer.i32(rva)
			writer.i32(8 + offsets.size * 2)
			for(i in 0 ..< offsets.size)
				writer.i16(offsets[i] or (10 shl 12))
		}

		val size = writer.pos - startPos
		writeDataDir(5, startRva, size)
	}



	/**
	 * - 00: Export
	 * - 01: Import
	 * - 02: Resource
	 * - 03: Exception
	 * - 04: Certificate
	 * - 05: Base relocation
	 * - 06: Debug
	 * - 07: Architecture
	 * - 08: Global pointer
	 * - 09: Thread storage
	 * - 10: Load config
	 * - 11: Bound import
	 * - 12: Import address table
	 * - 13: Delay import
	 * - 14: COM descriptor
	 * - 15: Reserved
	 */
	private fun writeDataDir(index: Int, pos: Int, size: Int) {
		writer.i32(dataDirsPos + index * 8, pos)
		writer.i32(dataDirsPos + index * 8 + 4, size)
	}



	private fun writeSymbolTable() {
		if(context.debugDirectives.isEmpty()) return

		val symTableStart = nextSectionPos
		writer.seek(nextSectionPos)

		val names = ArrayList<Pair<Int, String>>()

		for(directive in context.debugDirectives) {
			val name = directive.name
			if(name.length > 8) {
				names += (writer.pos + 4) to name
				writer.i64(0)
			} else
				writer.ascii64(name)
			writer.i32(directive.pos)
			writer.i16(1)
			writer.i16(0)
			writer.i8(2)
			writer.i8(0)
		}

		if(names.isNotEmpty()) {
			val stringTableStart = writer.pos
			writer.i32(0)

			for((pos, name) in names) {
				val stringPos = writer.pos - stringTableStart
				writer.i32(pos, stringPos)
				writer.ascii(name)
				writer.i8(0)
			}

			val size = writer.pos - stringTableStart
			writer.i32(stringTableStart, size)
		}

		writer.align(fileAlignment)
		writer.i32(symbolTablePosPos, symTableStart)
		writer.i32(numSymbolsPos, context.debugDirectives.size)
	}



	private fun writeImports() {
		val dlls = context.dllImports.values
		if(dlls.isEmpty()) return

		val writer = context.rdataWriter

		val idtsRva  = Section.RDATA.data.rva + context.rdataWriter.pos
		val idtsPos  = writer.pos
		val idtsSize = dlls.size * 20 + 20
		val offset   = idtsPos - idtsRva

		writer.zero(idtsSize)

		for((dllIndex, dll) in dlls.withIndex()) {
			val idtPos = idtsPos + dllIndex * 20
			val dllNamePos = writer.pos

			writer.ascii(dll.name.string)
			writer.ascii(".dll")
			writer.i8(0)
			writer.align(8)

			val iltPos = writer.pos

			writer.zero(dll.imports.size * 8 + 8)

			val iatPos = writer.pos

			writer.zero(dll.imports.size * 8 + 8)

			for((importIndex, import) in dll.imports.values.withIndex()) {
				writer.i32(iltPos + importIndex * 8, writer.pos - offset)
				writer.i32(iatPos + importIndex * 8, writer.pos - offset)
				writer.i16(0)
				writer.asciiNT(import.name.string)
				writer.align2()
				import.pos = iatPos + importIndex * 8 - idtsPos
			}

			writer.i32(idtPos, iltPos - offset)
			writer.i32(idtPos + 12, dllNamePos - offset)
			writer.i32(idtPos + 16, iatPos - offset)
		}

		writeDataDir(1, idtsRva, dlls.size * 20 + 20)

		writer.align(16)
	}



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
		writer.i64(imageBase)  // imageBase
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
		writer.seek(0x400)     // section headers (fill in later)

		nextSectionPos = writer.pos.roundToFile
		nextSectionRva = sectionAlignment
	}



	private fun writeSectionData(
		name           : String,
		virtualSize    : Int,
		virtualAddress : Int,
		rawDataSize    : Int,
		rawDataPos     : Int,
		flags          : Int,
	) {
		writer.seek(sectionHeadersPos + numSections++ * 40)
		writer.ascii64(name)
		writer.i32(virtualSize)
		writer.i32(virtualAddress)
		writer.i32(rawDataSize)
		writer.i32(rawDataPos)
		writer.zero(12)
		writer.i32(flags)
		writer.seek(nextSectionPos)
	}



	private fun writeSection(
		section   : Section,
		name      : String,
		flags     : Int,
		size      : Int,
		extraSize : Int,
	) {
		val rawDataSize    = size.roundToFile  // Must be aligned to fileAlignment
		val virtualSize    = size + extraSize  // No alignment requirement
		val rawDataPos     = if(size != 0) nextSectionPos else 0 // Must be aligned to fileAlignment
		val virtualAddress = nextSectionRva // Must be aligned to sectionAlignment

		nextSectionPos += rawDataSize
		nextSectionRva += virtualSize.roundToSection

		writeSectionData(name, virtualSize, virtualAddress, rawDataSize, rawDataPos, flags)

		sections[section.ordinal] = SectionData(virtualSize, virtualAddress, rawDataPos)
	}



	// Relocations



	private val PosSymbol.address get() = section.data.rva + pos



	private fun resolveImmRec(node: AstNode, regValid: Boolean): Long {
		if(node is IntNode) return node.value
		if(node is UnaryNode) return node.calculate(regValid, ::resolveImmRec)
		if(node is BinaryNode) return node.calculate(regValid, ::resolveImmRec)

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



	private val pages = HashMap<Int, IntList>()



	private fun Reloc.writeRelocation() {
		val value = if(rel)
			resolveImm(node) - (sec.data.rva + pos + width.bytes + offset)
		else
			resolveImm(node)

		writer.seek(sec.data.pos + pos)
		writer.writeWidth(width, value)
	}



}



private const val imageBase = 0x400000L

private const val sectionAlignment = 0x1000

private const val fileAlignment = 0x200

private const val numSectionsPos = 70

private const val entryPointPos = 104

private const val imageSizePos = 144

private const val idataDirPos = 208

private const val dataDirsPos = 200

private const val relocDirPos = 240

private const val sectionHeadersPos = 328

private const val symbolTablePosPos = 76

private const val numSymbolsPos = 80

private val Int.roundToFile get() = (this + fileAlignment - 1) and -fileAlignment

private val Int.roundToSection get() = (this + sectionAlignment - 1) and -sectionAlignment