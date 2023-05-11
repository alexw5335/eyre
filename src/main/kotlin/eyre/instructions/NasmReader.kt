package eyre.instructions

import java.nio.file.Files
import java.nio.file.Paths

class NasmIns(
	val mnemonic: String,
	val op1: String?,
	val op2: String?,
	val op3: String?,
	val op4: String?,
)



class NasmReader(private val chars: CharArray) {

	private var pos = 0

	private var lineNumber = 1

	fun read() {
		try {
			readInternal()
		} catch(e: Exception) {
			System.err.println("Error on line $lineNumber: ${line(lineNumber)}")
			e.printStackTrace()
		}
	}

	private fun readInternal() {
		while(pos < chars.size) {
			when(chars[pos]) {
				';'     -> skipLine()
				'\n'    -> { pos++; lineNumber++ }
				'\r'    -> pos++
				'#'     -> break
				Char(0) -> return
				else    -> readInstruction()
			}
		}
	}

	private fun line(lineNumber: Int): String {
		pos = 0
		var count = 0

		if(lineNumber == 0) return readLine()

		while(pos < chars.size) {
			if(chars[pos++] == '\n') {
				count++
				if(count == lineNumber)
					return readLine()
			}
		}
		error("Invalid line number: $lineNumber")
	}

	private fun readLine() = readUntil {
		it == '\n' || it == Char(0)
	}

	private fun skipLine() {
		while(pos < chars.size && chars[pos] != '\n') pos++
	}

	private fun readUntil(block: (Char) -> Boolean) = buildString {
		while(pos < chars.size && !block(chars[pos])) append(chars[pos++])
	}

	private fun skipUntil(block: (Char) -> Boolean) {
		while(pos < chars.size && !block(chars[pos])) pos++
	}

	private fun skipWhitespace() {
		while(pos < chars.size && chars[pos].isWhitespace()) pos++
	}

	private fun readInstruction() {
		val mnemonic = readUntil { it.isWhitespace() }
		skipWhitespace()
		val operands = readUntil { it.isWhitespace() }.split(',')
		skipWhitespace()
		
		if(chars[pos++] != '[') {
			skipLine()
			return
		}

		skipWhitespace()
		val components = readUntil { it == ']'}.split(' ', '\t').filter(String::isNotEmpty)
		skipUntil { it == ']' }
		pos++
		skipWhitespace()
		val extras = readUntil { it.isWhitespace() }.split(',')

		if("NOLONG" in extras || "NEVER" in extras || "UNDOC" in extras || "OBSOLETE" in extras) {
			skipLine()
			return
		}

		parseInstruction(mnemonic, operands, components, extras)
		skipLine()
	}


	private val set = HashSet<String>()

	private fun parseInstruction(
		mnemonic   : String,
		operands   : List<String>,
		components : List<String>,
		extras     : List<String>
	) {
		for(e in extras) {
			if(e in set) continue
			set += e
			println(e)
		}
		//for((index, operand) in operands.withIndex()) {
		//	val operandsString = operands.joinToString {  }
		//}
	}

	// VADDPS {k1} {z} {er} ymm0, [ymm0]


}



fun main() {
	//val string = Files.readString(Paths.get("instructions.txt"))
	//val chars = CharArray(string.length + 8)
	//string.toCharArray(chars)
	//NasmReader(chars).read()

	val lines = Files.readAllLines(Paths.get("instructions.txt"))

	for((index, line) in lines.withIndex()) {
		if(line.isEmpty() || line.startsWith(';')) continue
		if(line.startsWith('~')) break

		try {
			readLine(line)
		} catch(e: Exception) {
			System.err.println("Error on line $index: $line")
			throw e
		}
	}
}



private fun readLine(line: String) {
	val beforeBrackets = line.substringBefore('[')
	if(beforeBrackets.length == line.length) return

	val firstSplit = beforeBrackets
		.split(' ', '\t')
		.filter(String::isNotEmpty)

	val mnemonic = firstSplit[0]
	val operands = firstSplit[1]

	val parts = line
		.substring(beforeBrackets.length + 1, line.indexOf(']'))
		.split(' ', '\t')
		.filter(String::isNotEmpty)

	val extras = line
		.substringAfter(']')
		.trim()
		.split(',')
		.filter(String::isNotEmpty)

	println("$mnemonic $operands $parts $extras")
}



private fun readParts() {}
