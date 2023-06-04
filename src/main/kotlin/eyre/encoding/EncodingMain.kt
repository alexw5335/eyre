package eyre.encoding

import eyre.util.Unique


private val extensions = setOf(
	NasmExt.CET, NasmExt.FPU, NasmExt.ENQCMD, NasmExt.HRESET,
	NasmExt.INVPCID, NasmExt.MPX, NasmExt.PCONFIG, NasmExt.PREFETCHI,
	NasmExt.PREFETCHWT1, NasmExt.RTM, NasmExt.SERIALIZE, NasmExt.SGX,
	NasmExt.TSXLDTRK, NasmExt.UINTR, NasmExt.VMX, NasmExt.WBNOINVD,
	NasmExt.WRMSRNS
)



fun main() {
	val reader = EncodingReader.create("encodings.txt")
	reader.readLines()
	reader.expandLines()
	//reader.readEncodings()
	val nasmReader = NasmReader("nasm.txt")
	nasmReader.readRawLines()
	nasmReader.filterLines()
	nasmReader.scrapeLines()
	nasmReader.determineOperands()
	nasmReader.convertLines()
	nasmReader.map()

	val mnemonics = HashSet<String>()

	nasmReader.lines.forEach {
		if(it.operands.any { it == NasmOp.MM || it == NasmOp.X } && it.vex == null && it.evex == null) {
			mnemonics += it.mnemonic
		}
	}

	val lines = nasmReader.lines.filter { it.mnemonic in mnemonics }

	lines.forEach { Unique.print(it.operands.joinToString("_"))}

/*	val map1 = nasmReader.mnemonicMap
	val map2 = HashMap<String, ArrayList<EncodingLine>>()

	for(encoding in reader.expandedLines) {
		map2.getOrPut(encoding.mnemonic, ::ArrayList).add(encoding)
	}

	outer@ for(line in reader.encodingLines) {
		val list = map1[line.mnemonic] ?: continue
		for(l in list)
			if(l.opcode == line.opcode && l.prefix == line.prefix && l.escape == line.escape)
				continue@outer
		println("${line.opcode.hex16Full} $line")
	}*/
}



/*@JvmInline
value class Encoding(val value: Long) {

	constructor(
		opcode : Int,
		prefix : Int,
		escape : Int,
		ext    : Int,
		mask   : Int,
		rexw   : Int,
		o16    : Int
	) : this(
		(opcode.toLong() shl OPCODE) or
		(prefix.toLong() shl PREFIX) or
		(escape.toLong() shl ESCAPE) or
		(ext.toLong()    shl EXT)    or
		(mask.toLong()   shl MASK)   or
		(rexw.toLong()   shl REXW)   or
		(o16.toLong()    shl O16)
	)

	val opcode  get() = ((value shr OPCODE) and 0xFFFF).toInt()
	val prefix  get() = ((value shr PREFIX) and 0b0011).toInt()
	val escape  get() = ((value shr ESCAPE) and 0b0011).toInt()
	val ext     get() = ((value shr EXT)    and 0b1111).toInt()
	val mask    get() = ((value shr MASK)   and 0x00FF).toInt()
	val rexw    get() = ((value shr REXW)   and 0b0001).toInt()
	val o16     get() = ((value shr O16)    and 0b0001).toInt()

	companion object {
		private const val OPCODE = 0
		private const val PREFIX = OPCODE + 16
		private const val ESCAPE = PREFIX + 2
		private const val EXT    = ESCAPE + 2
		private const val MASK   = EXT    + 4
		private const val REXW   = MASK   + 8
		private const val O16    = REXW   + 1
	}

}*/