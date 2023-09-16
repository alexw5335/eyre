package eyre

import eyre.gen.NasmEnc

class EncGroup(val mnemonic: Mnemonic) {
	val encs = ArrayList<NasmEnc>()
	var isJcc = false

}