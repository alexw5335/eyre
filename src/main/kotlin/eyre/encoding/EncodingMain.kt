package eyre.encoding

import eyre.Mnemonic
import eyre.util.Unique


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



fun main() {
	val reader = EncodingReader.create("encodings.txt")
	reader.readLines()
	reader.expandLines()
	reader.readEncodings()
	val nasmReader = NasmReader("nasm.txt")
	nasmReader.readRawLines()
	nasmReader.filterLines()
	nasmReader.scrapeLines()
	nasmReader.filterExtensions(extensions)
	nasmReader.determineOperands()
	nasmReader.convertLines()
	nasmReader.map()

	/*outer@ for(line in reader.expandedLines) {
		if(line.mnemonic !in nasmReader.mnemonicMap) {
			Unique.print(line.mnemonic)
			continue
		}

		val list = nasmReader.mnemonicMap[line.mnemonic]!!

		for(l in list) {
			when {
				l.opcode != line.opcode -> continue
				l.prefix != line.prefix -> continue
				l.escape != line.escape -> continue
				l.extension != line.extension -> continue
				else -> continue@outer
			}
		}

		println("${line.prefix} ${line.escape} /${line.extension} ${line.opcode.hexc16}  ${line.mnemonic}  ${line.ops}")
		for(l in list)
			println("${l.prefix} ${l.escape} /${l.extension} ${l.opcode.hexc16}  ${l.mnemonic}  ${l.operands.joinToString("_")}")
		println()
	}*/

	val map = Mnemonic.values.associateBy { it.name }
	for(encoding in nasmReader.encodings)
		if(encoding.mnemonic !in map)
			Unique.print(encoding.mnemonic)
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