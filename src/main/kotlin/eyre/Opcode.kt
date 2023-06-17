package eyre



class ModRM(private val value: Int) {

	constructor(mod: Int, reg: Int, rm: Int) : this((mod shl 6) or (reg shl 3) or rm)

	val modrm get() = value
	val mod   get() = value shr 6
	val reg   get() = (value shr 3) and 0b111
	val rm    get() = value and 0b111

}



class Rex(private val value: Int) {

	constructor(w: Int, r: Int, x: Int, b: Int, force: Int, ban: Int) : this(
		(w shl 3) or
		(r shl 2) or
		(x shl 1) or
		(b shl 0) or
		(force shl 4) or
		(ban shl 5)
	)

	val w      get() = (value shr 3) and 1
	val r      get() = (value shr 2) and 1
	val x      get() = (value shr 1) and 1
	val b      get() = (value shr 0) and 1
	val rex    get() = value and 0xFF
	val forced get() = (value shr 4) and 1 == 1
	val banned get() = (value shr 5) and 1 == 1

}



class Opcode(private val value: Int) {

	val opcode get() = value and 0xFFFF

	val escape get() = (value shr 16) and 0b1111

	val prefix get() = (value shr 20) and 0b1111

	companion object {
		const val E0F = 1
		const val E00 = 2
		const val E38 = 3
		const val E3A = 4
		const val P66 = 1
		const val P67 = 2
		const val PF2 = 3
		const val PF3 = 4
	}

}



inline fun Opcode(block: Opcode.Companion.() -> Int) = Opcode(block(Opcode))