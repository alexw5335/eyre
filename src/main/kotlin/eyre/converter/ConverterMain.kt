package eyre.converter

import org.apache.pdfbox.tools.PDFText2HTML
import org.apache.tika.parser.pdf.PDFParser
import org.xml.sax.ContentHandler
import java.nio.file.Files
import java.nio.file.Paths



private class ManualReader(val chars: CharArray) {

	private var pos = 0

	private val headers = ArrayList<Int>()

	fun read() {
		while(pos < chars.size) {
			val char = chars[pos]
			if(char == Char(0)) break

			if(atString("\n## ", true)) {
				val startPos = pos
				val string = readLine()
				val valid = string.contains("—") || string.contains(" - ") || string.contains(" – ")
				if(valid) headers.add(startPos)
				continue
			}

			pos++
		}

		headers.add(pos - 1)

		for(i in 0 until headers.size - 2) {
			val string = String(chars, headers[i], headers[i + 1] - headers[i])
			Files.writeString(Paths.get("test/$i.txt"), string)
		}
	}

	private fun atString(string: String, advance: Boolean = false): Boolean {
		for(i in string.indices)
			if(chars[pos + i] != string[i])
				return false
		if(advance)
			pos += string.length
		return true
	}

	private fun readLine() = buildString {
		while(chars[pos] != '\n') append(chars[pos++])
	}

	private fun advanceTo(string: String) {
		while(pos < chars.size) if(atString(string)) break else pos++
	}

	private fun readUntil(string: String) = buildString {
		while(pos < chars.size) {
			if(atString(string)) return@buildString
			append(chars[pos++])
		}
	}

}



fun main() {
	val string = Files.readString(Paths.get("test/manual.txt"))
	val chars = CharArray(string.length + 128)
	string.toCharArray(chars)
	ManualReader(chars).read()
}