package eyre.encoding

import eyre.EncodingGroup
import eyre.Mnemonic
import eyre.nasm.*
import eyre.util.NativeReader
import eyre.util.NativeWriter
import java.nio.file.Files
import java.nio.file.Paths



private val extensions = setOf(
	NasmExt.CET, NasmExt.FPU, NasmExt.ENQCMD, NasmExt.HRESET,
	NasmExt.INVPCID, NasmExt.MPX, NasmExt.PCONFIG, NasmExt.PREFETCHI,
	NasmExt.PREFETCHWT1, NasmExt.RTM, NasmExt.SERIALIZE, NasmExt.SGX,
	NasmExt.TSXLDTRK, NasmExt.UINTR, NasmExt.VMX, NasmExt.WBNOINVD,
	NasmExt.WRMSRNS
)



fun main() {
	val reader = EncodingReader.create("encodings.txt")
	reader.read()
	val nasmReader = NasmReader("nasm.txt")
	nasmReader.readRawLines()
	nasmReader.filterLines()
	nasmReader.scrapeLines()
	nasmReader.filterExtensions(extensions)
	nasmReader.determineOperands()
	nasmReader.convertLines()

	NativeWriter.write("encodings.bin") { writer ->
		for(opCount in reader.groups.indices) {
			val groups = reader.groups[opCount]
			for(group in groups) {
				if(group == null) {
					writer.i8(0)
					continue
				}

				writer.i8(group.encodings.size)
				writer.i32(group.operands)
				writer.i32(group.specs)
				for(encoding in group.encodings)
					writer.i64(encoding)
			}
		}
	}

	val encodings = readEncodings("encodings.bin")
}



fun readEncodings(path: String): Array<Array<EncodingGroup?>> {
	val array = Array(5) { arrayOfNulls<EncodingGroup>(Mnemonic.values.size) }
	val reader = NativeReader(Files.readAllBytes(Paths.get(path)))

	for(i in array.indices) {
		for(j in 0 until Mnemonic.values.size) {
			val encodingCount = reader.s8()
			if(encodingCount == 0) continue
			val operands = reader.i32()
			val specs = reader.i32()
			val encodings = LongArray(encodingCount)
			for(k in encodings.indices)
				encodings[k] = reader.i64()
			array[i][j] = EncodingGroup(operands, specs, encodings)
		}
	}

	return array
}



private fun writeEncodings(reader: EncodingReader) = NativeWriter.write("encodings.bin") { writer ->
	for(opCount in reader.groups.indices) {
		val groups = reader.groups[opCount]
		for(group in groups) {
			if(group == null) {
				writer.i8(0)
				continue
			}

			writer.i8(group.encodings.size)
			writer.i32(group.operands)
			writer.i32(group.specs)
			for(encoding in group.encodings)
				writer.i64(encoding)
		}
	}
}