package eyre.encoding

import eyre.nasm.*
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

/*	for(opCount in reader.groups.indices) {
		val groups = reader.groups[opCount]
		println("val map$opCount = arrayOf<EncodingGroup?>(")
		var nullCount = 0
		for(group in groups) {
			if(group == null) {
				if(nullCount > 10) {
					println()
					nullCount = 0
				}
				if(nullCount == 0) print("\t")
				print("null,")
				nullCount++
				continue
			}
			if(nullCount > 0) println()
			nullCount = 0
			print("\tEncodingGroup(${group.operands}, ${group.specs}, longArrayOf(")
			for((i, e) in group.encodings.withIndex()) {
				print("$e")
				if(i != group.encodings.size - 1)
					print(", ")
			}
			println(")),")
		}
		println(")")
		println()
	}*/
}