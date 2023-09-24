package eyre.gen

import eyre.*

class NasmLine(val raw: RawNasmLine) {

	val mnemonic = raw.mnemonic
	var arch = NasmArch.NONE
	val extensions = ArrayList<NasmExt>()

	var opSize: Width? = null
	var sm = false
	var ar = -1
	var immType = NasmImm.NONE

	var prefix = Prefix.NONE
	var escape = Escape.NONE
	var opcode = 0
	var oplen = 0
	var ext = -1
	var rexw = 0
	var o16 = 0
	var a32 = 0
	var enc = NasmOpEnc.NONE
	var pseudo = -1
	var cc = false
	var opreg = false
	var modrm = false
	var odf = false

	var vex: String? = null
	var isEvex = false
	var vsib: NasmVsib? = null
	var is4 = false
	var tuple: NasmTuple? = null
	var k   = false
	var z   = false
	var sae = false
	var er  = false
	var b16 = false
	var b32 = false
	var b64 = false
	var rs2 = false
	var rs4 = false
	var nds = false
	var ndd = false
	var dds = false
	var star = false
	var vexl = NasmVexL.LIG
	var vexw = NasmVexW.WIG // Assuming that omission of VEX.W implies WIG (not certain)
	var map5 = false
	var map6 = false

	val ops = ArrayList<NasmOp>()

	fun addOpcode(value: Int) {
		if(vex == null && oplen == 0) {
			when(value) {
				0x0F -> if(escape == Escape.NONE) { escape = Escape.E0F; return }
				0x38 -> if(escape == Escape.E0F) { escape = Escape.E38; return }
				0x3A -> if(escape == Escape.E0F) { escape = Escape.E3A; return }
			}
		}

		opcode = opcode or (value shl (oplen++ shl 3))
	}

	override fun toString() = raw.toString()


}