package eyre.encoding

object Encodings {

	val groups = EncodingReader.create("encodings.txt").let { it.read(); it.groups }

}