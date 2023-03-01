package eyre

import eyre.util.BitList
import eyre.util.IntList
import java.nio.file.Path

class SrcFile(val path: Path, val relPath: Path) {

	// Valid after the lexer has been called
	// Invalid after the lexer has been used to lex another file
	lateinit var tokens      : List<Token>
	lateinit var tokenLines  : IntList
	lateinit var newlines    : BitList
	lateinit var terminators : BitList

	// Valid after the parser has been called
	lateinit var nodeLines   : IntArray
	lateinit var nodes       : List<AstNode>

	// Used by the resolver
	var resolving = false
	var resolved = false

}