package eyre

import eyre.util.NativeWriter



class Linker(private val context: CompilerContext) {


	private val writer = context.linkWriter

	private val sections = context.sections

	private var numSections = 0

	private var nextSectionRva = 0

	private var nextSectionPos = 0

	private val Section.data get() = sections[ordinal] ?: error("Invalid section: $this")



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
		writer.advance(16 * 8)     // dataDirectories (fill in later)
		writer.seek(0x200)         // section headers (fill in later)

		nextSectionPos = fileAlignment
		nextSectionRva = sectionAlignment
	}



	private fun writeSection(
		name: String,
		characteristics: Int,
		bytes: ByteArray?,
		size: Int,
		extraSize: Int,
		section: Section
	) {
		val virtualAddress = nextSectionRva   // Must be aligned to sectionAlignment
		val rawDataPos     = nextSectionPos   // Must be aligned to fileAlignment
		val rawDataSize    = size.roundToFile // Must be aligned to fileAlignment
		val virtualSize    = size + extraSize // No alignment requirement, may be smaller than rawDataSize

		writer.seek(rawDataPos)

		if(bytes != null)
			writer.bytes(bytes, length = size)
		else
			writer.advance(size)
		nextSectionPos += rawDataSize
		nextSectionRva += virtualSize.roundToSection

		writer.zeroTo(nextSectionPos)

		writer.seek(sectionHeadersPos + numSections * 40)
		numSections++

		writer.ascii64(name)
		writer.i32(virtualSize)
		writer.i32(virtualAddress)
		writer.i32(rawDataSize)
		writer.i32(rawDataPos)
		writer.zero(12)
		writer.i32(characteristics)

		writer.seek(nextSectionPos)

		sections[section.ordinal] = SectionData(size = virtualSize, rva = virtualAddress, pos = rawDataPos)
	}



	private fun writeSections() {
		writeSection(
			".text",
			0x60000020,
			context.textWriter.getTrimmedBytes(),
			context.textWriter.pos,
			0,
			Section.TEXT
		)

		if(context.dataWriter.pos != 0) writeSection(
			".data",
			0xC0_00_00_40L.toInt(),
			context.dataWriter.getTrimmedBytes(),
			context.dataWriter.pos,
			0,
			Section.DATA
		)
	}



	fun link() {
		writeHeaders()
		writeSections()
		writeImports()
		val finalSize = writer.pos
		context.relocations.forEach { it.writeRelocation() }

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



	private fun writeImports() {
		val dlls = context.dlls
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

			writer.zero(dll.symbols.size * 8 + 8)

			val iatPos = writer.pos

			writer.zero(dll.symbols.size * 8 + 8)

			for((importIndex, import) in dll.symbols.withIndex()) {
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

		writeSection(
			".idata",
			0x40000040,
			null,
			writer.pos - idtsPos,
			0,
			Section.IDATA
		)
	}



	// Relocations



	private val PosSymbol.address get() = section.data.rva + pos



	private fun resolveImmRec(node: AstNode, regValid: Boolean): Long {
		if(node is IntNode) return node.value

		if(node is UnaryNode) return node.op.calculate(
			resolveImmRec(node.node, regValid && node.op == UnaryOp.POS)
		)

		if(node is BinaryNode) return node.op.calculate(
			resolveImmRec(node.left, regValid && node.op.isLeftRegValid),
			resolveImmRec(node.right, regValid && node.op.isRightRegValid)
		)

		if(node is SymProviderNode) {
			val symbol = node.symbol ?: error("Unresolved symbol")
			if(symbol is PosSymbol) {
				return symbol.address.toLong()
			} else {
				error("Non-positional symbols are not yet supported")
			}
		}

		error("Invalid imm node: $node")
	}



	private fun resolveImm(node: AstNode): Long {
		return resolveImmRec(node, true)
	}



	private fun Relocation.writeRelocation() {
		if(type == Relocation.Type.DEFAULT) {
			val value = resolveImm(node)
			writer.seek(section.data.pos + pos)
			writer.writeWidth(width, value)
		} else if(type == Relocation.Type.RIP_RELATIVE) {
			val value = resolveImm(node) - (section.data.rva + pos + width.bytes + offset)
			writer.seek(section.data.pos + pos)
			writer.writeWidth(width, value)
		} else {
			error("Absolute relocations not yet supported")
		}
	}


}



private val Int.roundToFile get() = (this + fileAlignment - 1) and -fileAlignment

private val Int.roundToSection get() = (this + sectionAlignment - 1) and -sectionAlignment

private const val sectionAlignment = 0x1000

private const val fileAlignment = 0x200

private const val numSectionsPos = 70

private const val entryPointPos = 104

private const val imageSizePos = 144

private const val idataDirPos = 208

private const val sectionHeadersPos = 328