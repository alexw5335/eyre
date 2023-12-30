package eyre

import java.nio.file.Path



class SrcFile(val index: Int, val path: Path, val relPath: Path) {
	val tokens = ArrayList<Token>()
	val nodes = ArrayList<Node>()
	var lineCount = 0
	var invalid = false
}



class SrcPos(val file: SrcFile, val line: Int)



class EyreError(val srcPos: SrcPos?, override val message: String) : Exception()