package eyre.encoding


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
	//nasmReader.filterExtensions(extensions)
	nasmReader.determineOperands()
	nasmReader.convertLines()

	for(encoding in nasmReader.encodings)
		if(encoding.operands.any { it.type == NasmOpType.MM })
			println(encoding)
}

