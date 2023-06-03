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
	nasmReader.map()

	val set = HashSet<String>()

	for(encoding in nasmReader.encodings)
		if(encoding.operands.any { it.type == NasmOpType.MM })
			set.add(encoding.mnemonic)

	val operands = HashSet<String>()

	for(s in set) {

		nasmReader.mnemonicMap[s]?.forEach {
			println(it)
			operands += it.operands.joinToString("_")
		}
	}
}



//X_M32
//MM_RM16_I8
//MMM64_MM
//X_XM128
//MM_R32_I8
//X_M16_I8
//X_RM64
//MM_MEM_I8
//X_MEM_I8
//X_MM
//X_R16_I8
//R32_X_I8
//M32_X
//RM64_MM
//MM_XM128
//R32_X
//RM64_X
//MM_MMM64_I8
//M16_X_I8
//X_XM128_I8
//MM_X
//MM_RM32
//X_M64
//M64_MM
//X_RM32
//MM_I8
//RM32_MM
//X_X
//RM32_X
//M64_X
//R64_X_I8
//MM_XM64
//R32_MM
//MM_MMM64
//R32_MM_I8
//MM_MM
//MM_RM64
//X_I8

