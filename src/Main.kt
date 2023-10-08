package eyre

import java.nio.file.Paths



fun main() {
	System.load(Paths.get("natives/natives.dll").toAbsolutePath().toString())
	Natives.init()
    Compiler.create("samples/simple").compile()
}