package eyre


/*
data class NdisasmIns(
	val addr: Long,
	val mnemonic: String,
	val ops: List<NdisasmOp>
)

sealed interface NdisasmOp

data class NdisasmMemOp(val base: NdisasmRegOp, val index: NdisasmRegOp, val scale: Int, val disp: Int, val rip: Boolean) : NdisasmOp

enum class NdisasmRegOp : NdisasmOp {
	AL,CL,DL,BL,SPL,BPL,SIL,DIL,R8B,R9B,R10B,R11B,R12B,R13B,R14B,R15B,
	AX,CX,DX,BX,SP,BP,SI,DI,R8W,R9W,R10W,R11W,R12W,R13W,R14W,R15W,
	EAX,ECX,EDX,EBX,ESP,EBP,ESI,EDI,R8D,R9D,R10D,R11D,R12D,R13D,R14D,R15D,
	RAX,RCX,RDX,RBX,RSP,RBP,RSI,RDI,R8,R9,R10,R11,R12,R13,R14,R15;
	companion object { val nameMap = entries.associateBy { it.name.lowercase() } }
}

data class NdisasmImmOp(val value: Long) : NdisasmOp



class NdisasmParser(val chars: CharArray, var length: Int) {

	var pos = 0

	private fun readPart(): String {
		val start = pos
		while(pos < length && chars[pos].isLetterOrDigit()) pos++
		return String(chars, start, pos - start)
	}

	private fun readOperand(): NdisasmOp {
		return if(chars[pos] == '[') {
			pos++
			var base = 0
			var index = 0
			var scale = 0
			if(chars[pos].isDigit()) {
				val base = readPart()
			}
			if(chars[pos] == '+') {
				pos++

			}
			while(chars[pos++] != ']') {
				val part = readPart()
				if(part[0].isLetter()) {

				} else if(part[0] == '+')
			}
			NdisasmRegOp.RAX
		} else if(chars[pos] == '0') {
			pos += 2
			NdisasmImmOp(readPart().toLong(16))
		} else {
			val regName = readPart()
			NdisasmRegOp.nameMap[regName] ?: error("Invalid register: $regName")
		}
	}

	fun parse(): NdisasmIns {
		println(String(chars, 0, length))
		val addr = String(chars, 0, 8).toLong(16)
		pos += 28
		val mnemonic = readPart()
		pos++
		val ops = ArrayList<NdisasmOp>()
		while(pos < length) {
			ops += readOperand()
			pos++
		}
		return NdisasmIns(addr, mnemonic, ops)
	}

}



fun test() {
	val process = Runtime.getRuntime().exec(arrayOf("ndisasm", "-b64", "build/code.bin"))

	val outBuilder = StringBuilder()
	val errBuilder = StringBuilder()

	val parser = NdisasmParser(CharArray(64), 0)

	val outThread = Thread {
		val reader = process.inputReader()
		while(true) when(val char = reader.read()) {
			-1 -> break
			0xA -> {
				if(parser.chars[0] != ' ') parser.parse().also(::println)
				parser.length = 0
				parser.pos = 0
			}
			else -> parser.chars[parser.length++] = Char(char)
		}
	}

	val errThread = Thread {
		val reader = process.errorReader()
		while(true) errBuilder.appendLine(reader.readLine() ?: break)
	}

	outThread.start()
	errThread.start()

	process.waitFor()

	if(outBuilder.isNotEmpty())
		print(outBuilder)

	if(errBuilder.isNotEmpty()) {
		print("\u001B[31m")
		print(errBuilder)
		print("\u001B[0m")
		error("Process failed")
	}
}*/