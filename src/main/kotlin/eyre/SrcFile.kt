package eyre

import java.nio.file.Path

class SrcFile(val path: Path, val relPath: Path) {

	lateinit var newlines    : BitArray
	lateinit var terminators : BitArray
	lateinit var tokenLines  : IntArray
}