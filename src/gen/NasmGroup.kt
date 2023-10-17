package eyre.gen

import eyre.Mnemonic

class NasmGroup(val mnemonic: Mnemonic) {
	val encs = ArrayList<NasmEnc>()
	val allEncs = ArrayList<NasmEnc>()
}