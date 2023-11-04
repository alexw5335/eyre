package eyre

class Linker(private val context: Context) {


	private val writer = context.linkWriter

	private var numSections = 0

	private var currentSecRva = 0

	private var currentSecPos = 0



	fun link() {
		writeHeaders()

		section(context.textSec, 0) {
			if(context.textWriter.isNotEmpty)
				writer.bytes(context.textWriter)
		}

		section(context.bssSec, context.bssSize) { }

		section(context.dataSec, 0) {
			if(context.dataWriter.isNotEmpty)
				writer.bytes(context.dataWriter)
		}

		section(context.rdataSec, 0) {
			if(context.rdataWriter.isNotEmpty)
				writer.bytes(context.rdataWriter)

			if(context.dllImports.isNotEmpty()) {
				writer.align(16)
				writeImports(currentSecRva + writer.pos - currentSecPos, writer.pos - currentSecPos, writer)
			}

			if(context.absRelocs.isNotEmpty()) {
				writer.align(16)
				writeAbsRelocs(currentSecRva + writer.pos - currentSecPos, writer)
			}
		}

		//for(symbol in context.symbols) {
		//	if(symbol is PosSymbol)
		//		context.debugDirectives += DebugDirective(symbol.qualifiedName, symbol.pos, symbol.section)
		//}
		//if(context.debugDirectives.isNotEmpty())
		//	writeSymbolTable()

		for(reloc in context.linkRelocs)
			reloc.writeRelocation()

		if(context.entryPoint != null)
			writer.i32(entryPointPos, context.addr(context.entryPoint!!))

		writer.i32(imageSizePos, currentSecRva)
		writer.i32(numSectionsPos, numSections)
	}



	private fun writeRelocations() {
		for(reloc in context.linkRelocs) {
			try {
				var value = resolveImm(reloc.node)
				if(reloc.rel)
					value -= context.addr(reloc.pos) + reloc.width.bytes + reloc.offset
				writer.at(context.pos(reloc.pos)) {
					writer.writeWidth(reloc.width, value)
				}
			} catch(e: EyreError) {
				val node = reloc.node as? TopNode ?: context.internalErr()
				if(e.srcPos.isNull) e.srcPos = node.srcPos
			}
		}
	}



	private fun writeAbsRelocs(startRva: Int, writer: BinWriter) {
		val pages = HashMap<Int, ArrayList<Int>>()

		for(reloc in context.absRelocs) {
			val value = resolveImm(reloc.node)
			val rva = context.addr(reloc.pos)
			val pageRva = (rva shr 12) shl 12
			pages.getOrPut(pageRva, ::ArrayList).add(rva - pageRva)
			writer.i64(context.pos(reloc.pos), value + imageBase)
		}

		val startPos = writer.pos

		for((rva, offsets) in pages) {
			writer.i32(rva)
			writer.i32(8 + offsets.size * 2)
			for(i in 0 ..< offsets.size)
				writer.i16(offsets[i] or (10 shl 12))
		}

		val size = writer.pos - startPos
		writeDataDir(5, startRva, size)
	}



	private fun writeDataDir(index: Int, pos: Int, size: Int) {
		writer.i32(dataDirsPos + index * 8, pos)
		writer.i32(dataDirsPos + index * 8 + 4, size)
	}



	private fun writeSymbolTable() {
		/*val symTableStart = currentSecPos
		writer.seek(currentSecPos)

		val names = ArrayList<Pair<Int, String>>()

		for(directive in context.debugDirectives) {
			val name = directive.name
			if(name.length > 8) {
				names += (writer.pos + 4) to name
				writer.i64(0)
			} else
				writer.ascii64(name)
			writer.i32(directive.pos)
			val secIndex = context.getIndex(directive.sec)
			if(secIndex == -1) context.internalError("Invalid section")
			writer.i16(secIndex + 1)
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
		*/
	}



	/**
	 * - [startRva]: The RVA of the start of the import data directory, relative to the image start
	 * - [startPos]: The pos of the start of the import data directory, relative to the section start
	 */
	private fun writeImports(startRva: Int, startPos: Int, writer: BinWriter) {
		val dlls = context.dllImports.values

		val idtsRva  = startRva
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
				import.pos = import.pos.withDisp(iatPos + importIndex * 8 - idtsPos + startPos)
			}

			writer.i32(idtPos, iltPos - offset)
			writer.i32(idtPos + 12, dllNamePos - offset)
			writer.i32(idtPos + 16, iatPos - offset)
		}

		writer.align(16)

		writeDataDir(1, idtsRva, dlls.size * 20 + 20)
	}



	private fun writeHeaders() {
		writer.i16(0x5A4D)
		writer.seek(0x3C)
		writer.i32(0x40)

		writer.i32(0x4550)     // signature
		writer.i16(0x8664)     // machine
		writer.i16(1)          // numSections    (fill in later)
		writer.i32(0)          // timeDateStamp
		writer.i32(0)          // pSymbolTable   (fill in later if present)
		writer.i32(0)          // numSymbols     (fill in later if present)
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

		// Make sure that the file alignment allows for 16 data directories and at least 4 section headers
		if(fileAlignment - writer.pos < 16*8 + 4*40)
			context.internalErr("Invalid file alignment")

		writer.pos = fileAlignment
		currentSecPos = fileAlignment
		currentSecRva = sectionAlignment
	}



	private inline fun section(sec: Section, uninitSize: Int, block: () -> Unit) {
		sec.addr = currentSecRva
		sec.pos = currentSecPos

		block()

		val size = writer.pos - currentSecPos
		val rawSize = size.roundToFile
		val virtualSize = size + uninitSize

		if(virtualSize == 0)
			return

		if(numSections == 4)
			context.internalErr("Maximum of four sections allowed")

		writer.at(sectionHeadersPos + numSections++ * 40) {
			writer.ascii64(sec.name)
			writer.i32(virtualSize)
			writer.i32(currentSecRva)
			writer.i32(rawSize)
			writer.i32(currentSecPos)
			writer.zero(12)
			writer.i32(sec.flags.toInt())
		}

		currentSecPos += rawSize
		currentSecRva += virtualSize.roundToSection

		writer.seek(currentSecPos)
	}



	// Relocations



	private fun resolveImmRec(node: Node, regValid: Boolean): Long {
		if(node is IntNode)
			return node.value

		if(node is UnNode)
			return node.calc(regValid, ::resolveImmRec)

		if(node is BinNode)
			return node.calc(regValid, ::resolveImmRec)

		context.err(SrcPos(), "Invalid immediate node (this should never happen here")
	}



	private fun resolveImm(node: Node): Long {
		return resolveImmRec(node, true)
	}



	private fun Reloc.writeRelocation() {
		val value = if(rel)
			resolveImm(node) - (context.addr(pos) + width.bytes + offset)
		else
			resolveImm(node)

		writer.at(context.pos(pos)) {
			writer.writeWidth(width, value)
		}
	}


}



private const val imageBase = 0x400000L

private const val sectionAlignment = 0x1000

private const val fileAlignment = 0x200

private const val numSectionsPos = 70

private const val entryPointPos = 104

private const val imageSizePos = 144

private const val dataDirsPos = 0xC8

private const val sectionHeadersPos = 0x148

private const val symbolTablePosPos = 76

private const val numSymbolsPos = 80

private val Int.roundToFile get() = (this + fileAlignment - 1) and -fileAlignment

private val Int.roundToSection get() = (this + sectionAlignment - 1) and -sectionAlignment