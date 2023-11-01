package eyre.gen

import eyre.AutoEnc
import eyre.Mnemonic

class EncGroup(val mnemonic: Mnemonic) {
	val encs = ArrayList<ParsedEnc>()
}