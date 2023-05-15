package eyre.instructions

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess



/*
Line reading
 */



private fun readLine(lineNumber: Int, line: String): NasmLine? {
	val beforeBrackets = line.substringBefore('[')
	if(beforeBrackets.length == line.length) return null

	val firstSplit = beforeBrackets
		.split(' ', '\t')
		.filter(String::isNotEmpty)

	val mnemonicString = firstSplit[0]
	val operandsString = firstSplit[1]

	val operands = operandsString.split(',')

	val parts = line
		.substring(beforeBrackets.length + 1, line.indexOf(']'))
		.split(' ', '\t')
		.filter(String::isNotEmpty)

	val extras = line
		.substringAfter(']')
		.trim()
		.split(',')
		.filter(String::isNotEmpty)

	if(extras.any(invalidExtras::contains)) return null

	for(e in extras) {
		if(e !in Arch.map && e !in Extension.map && e !in Extra.map && e !in ignoredExtras)
			error("Invalid extra: $e")
	}

	if("sbyte" in operandsString) return null

	val arch        = extras.firstNotNullOfOrNull(Arch.map::get) ?: Arch.NONE
	val extension   = extras.firstNotNullOfOrNull(Extension.map::get) ?: Extension.NONE
	val sizesString = extras.mapNotNull(Extra.map::get).joinToString("_")
	val size        = if(sizesString.isEmpty()) Size.NONE else Size.map[sizesString] ?: error(sizesString)

	return NasmLine(
		lineNumber,
		mnemonicString,
		operandsString,
		operands,
		parts,
		extras,
		arch,
		extension
	)
}



private fun readLines(): List<NasmLine> {
	val lines = Files.readAllLines(Paths.get("instructions.txt"))
	val nasmLines = ArrayList<NasmLine>()

	for((index, line) in lines.withIndex()) {
		if(line.isEmpty() || line.startsWith(';')) continue
		if(line.startsWith('~')) break

		try {
			readLine(index + 1, line)?.let(nasmLines::add)
		} catch(e: Exception) {
			System.err.println("Error on line ${index + 1}: $line")
			throw e
		}
	}

	return nasmLines
}



private val lines = readLines()



/*
Main
 */



fun main() {
	for(line in lines) {
		val list = ArrayList<Extra>()
		for(extra in line.extras) {
			Extra.map[extra]?.let(list::add)
		}
		//if(list.size > 1) println(line)
	}
}



private fun NasmLine.error(message: String): Nothing {
	System.err.println("Error on line $lineNumber:")
	System.err.println("\t$this")
	System.err.println("\t$message")
	exitProcess(1)
}



private fun NasmLine.determineOperands(): NasmOperands {
	if("SM" in extras)
		return smMap[operandsString] ?: error("Unrecognised SM operands")

	error("invalid operands")
}
