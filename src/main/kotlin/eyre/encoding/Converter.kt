package eyre.encoding



private val extensions = setOf(
	// General-purpose
	NasmExt.CET, NasmExt.FPU, NasmExt.ENQCMD, NasmExt.HRESET,
	NasmExt.INVPCID, NasmExt.MPX, NasmExt.PCONFIG, NasmExt.PREFETCHI,
	NasmExt.PREFETCHWT1, NasmExt.RTM, NasmExt.SERIALIZE, NasmExt.SGX,
	NasmExt.TSXLDTRK, NasmExt.UINTR, NasmExt.VMX, NasmExt.WBNOINVD,
	NasmExt.WRMSRNS,

	// MMX/SSE
	NasmExt.MMX,
	NasmExt.SSE,
	NasmExt.SSE2,
	NasmExt.SSE3,
	NasmExt.SSE41,
	NasmExt.SSE42,
	NasmExt.SSE4A,
	NasmExt.SSE5,
	NasmExt.SSSE3
)



class Converter {


	val manualReader = EncodingReader.create("encodings.txt")

	val nasmReader = NasmReader("nasm.txt")



	init {
		manualReader.readLines()
		manualReader.expandLines()
		manualReader.readEncodings()
		nasmReader.readRawLines()
		nasmReader.filterLines()
		nasmReader.scrapeLines()
		nasmReader.filterExtensions(extensions)
		nasmReader.determineOperands()
		nasmReader.convertLines()
		nasmReader.map()
	}


}