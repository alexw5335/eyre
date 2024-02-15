package eyre

import java.util.function.Function

class Linker(private val context: Context) {


	private val writer = context.linkWriter

	private var numSections = 0

	private var currentSecRva = 0

	private var currentSecPos = 0



	fun link() {
		writeHeaders()

		// .text must be the first section
		section(context.textSec, 0) {
			writer.bytes(context.textWriter)
		}

		section(context.bssSec, context.bssSize) { }

		section(context.dataSec, 0) {
			writer.bytes(context.dataWriter)
		}

		section(context.rdataSec, 0) {
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

		for(reloc in context.linkRelocs)
			reloc.write()

		if(context.entryPoint == null)
			context.err(null, "Missing main function")

		writer.i32(entryPointPos, context.entryPoint!!.addr)
		writer.i32(imageSizePos, currentSecRva)
		writer.i32(numSectionsPos, numSections)
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



	private fun section(sec: Section, uninitSize: Int, block: () -> Unit) {
		sec.addr = currentSecRva
		sec.pos = currentSecPos

		block()

		val size = writer.pos - sec.pos
		val rawSize = size.roundToFile
		val virtSize = size + uninitSize
		sec.size = virtSize

		if(virtSize == 0)
			return
		if(numSections == 4)
			context.internalErr("Max of 4 sections allowed")

		writer.at(sectionHeadersPos + numSections++ * 40) {
			writer.ascii64(sec.name)
			writer.i32(virtSize)
			writer.i32(sec.addr)
			writer.i32(rawSize)
			writer.i32(sec.pos)
			writer.zero(12)
			writer.i32(sec.flags.toInt())
		}

		currentSecPos += rawSize
		currentSecRva += virtSize.roundToSection

		writer.seek(currentSecPos)
	}



	private fun writeDataDir(index: Int, pos: Int, size: Int) {
		writer.i32(dataDirsPos + index * 8, pos)
		writer.i32(dataDirsPos + index * 8 + 4, size)
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

			writer.ascii(dll.name)
			writer.ascii(".dll")
			writer.i8(0)
			writer.align(8)

			val iltPos = writer.pos
			writer.zero(dll.imports.size * 8 + 8)
			val iatPos = writer.pos
			writer.zero(dll.imports.size * 8 + 8)

			var importIndex = 0
			for((importName, importPos) in dll.imports) {
				writer.i32(iltPos + importIndex * 8, writer.pos - offset)
				writer.i32(iatPos + importIndex * 8, writer.pos - offset)
				writer.i16(0)
				writer.asciiNT(importName)
				writer.align2()
				importPos.sec = context.rdataSec
				importPos.disp = iatPos + importIndex * 8 - idtsPos + startPos
				importIndex++
			}

			writer.i32(idtPos, iltPos - offset)
			writer.i32(idtPos + 12, dllNamePos - offset)
			writer.i32(idtPos + 16, iatPos - offset)
		}

		writer.align(16)

		writeDataDir(1, idtsRva, dlls.size * 20 + 20)
	}



	// Relocations



	private fun writeAbsRelocs(startRva: Int, writer: BinWriter) {
		val pages = HashMap<Int, ArrayList<Int>>()

		for(reloc in context.absRelocs) {
			val value = resolveImm(reloc.node)
			val rva = reloc.pos.addr
			val pageRva = (rva shr 12) shl 12
			pages.getOrPut(pageRva, ::ArrayList).add(rva - pageRva)
			writer.i64(reloc.pos.pos, value + imageBase)
		}

		val startPos = writer.pos

		// First 12 bits are the offset from the page RVA
		// Last 4 bits are the type (IMAGE_REL_BASED_DIR64, 0xA)
		for((rva, offsets) in pages) {
			writer.i32(rva)
			writer.i32(8 + offsets.size * 2)
			for(i in 0 ..< offsets.size)
				writer.i16(offsets[i] or (10 shl 12))
		}

		val size = writer.pos - startPos
		writeDataDir(5, startRva, size)
	}



	private fun resolveImmRec(node: Node, regValid: Boolean): Long {
		fun sym(sym: Sym?): Long {
			if(sym == null) context.err(node.srcPos, "Unresolved symbol")
			if(sym is PosSym) return sym.addr.toLong()
			if(sym is IntSym) return sym.intValue
			context.err(node.srcPos, "Invalid symbol")
		}

		return when(node) {
			is RefNode     -> node.intSupplier?.invoke() ?: context.err(node.srcPos, "Invalid ref node")
			is StringNode  -> return node.litPos!!.addr.toLong()
			is IntNode     -> node.value
			is UnNode      -> node.calc(regValid, ::resolveImmRec)
			is BinNode     -> node.calc(regValid, ::resolveImmRec)
			is DllCallNode -> node.importPos!!.addr.toLong()
			is NameNode    -> sym(node.sym)
			is DotNode     -> sym(node.sym)
			is ArrayNode   -> sym(node.sym)
			is RegNode     -> 0
			else           -> context.err(node.srcPos, "Invalid immediate node: $node")
		}
	}

	private fun resolveImm(node: Node) = resolveImmRec(node, true)

	private fun Reloc.write() {
		val value = if(rel)
			resolveImm(node) - (pos.addr + width.bytes + offset)
		else
			resolveImm(node)

		writer.at(pos.pos) {
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