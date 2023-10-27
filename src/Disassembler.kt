package eyre

import eyre.gen.EncGen
import eyre.gen.NasmEnc

/**
 * Only designed to operate on complete data
 */
fun disasm(bytes: IntArray) {
	var pos = 0
	fun atEnd() = pos >= bytes.size

	// 66, 67, 9B, F0, F2, F3
	var prefixes = 0
	var rex = 0

	fun prefixOnly() {
		if(prefixes and 0b100 != 0)
			println("FWAIT")
		else
			error("Invalid encoding")
	}

	while(!atEnd()) {
		when(val byte = bytes[pos++]) {
			0x66 -> { if(prefixes and 1 != 0) prefixOnly(); prefixes = prefixes or 1 }
			0x67 -> { if(prefixes and 2 != 0) prefixOnly(); prefixes = prefixes or 2 }
			0x9B -> { if(prefixes and 4 != 0) prefixOnly(); prefixes = prefixes or 4}
			0xF0 -> { if(prefixes and 8 != 0) prefixOnly(); prefixes = prefixes or 8 }
			0xF2 -> { if(prefixes and 16 != 0) prefixOnly(); prefixes = prefixes or 16 }
			0xF3 -> { if(prefixes and 32 != 0) prefixOnly(); prefixes = prefixes or 32 }
			in 0x40..0x4F -> { if(rex != 0) prefixOnly(); rex = byte }
			else -> { pos--; break }
		}
	}

	if(atEnd()) prefixOnly()
	val opcode = bytes[pos++]
/*	val group = EncGen.disasmGroups[0][opcode]
	val rw = (rex shr 3) and 1
	fun match(enc: NasmEnc) = enc.prefixes and prefixes == enc.prefixes && enc.rw == rw

	if(group.modrm) {
		if(atEnd()) prefixOnly()
		val modrm = bytes[pos++]
		for(e in group.encs) {
			if(!match(e)) continue
			val mod = modrm shr 6
			val reg = (modrm shr 3) and 7
			val rm = modrm and 7
			if(group.ext && reg != e.ext) continue
		}
	} else {
		for(e in group.encs) {
			if(!match(e)) continue
			if(e.immWidth != null) {

			}
		}
	}*/
}