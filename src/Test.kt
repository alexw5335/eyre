package eyre


enum class Test {
	A,B,C,D;
}

var test = Test.A

fun test() {
	when(test) {
		Test.A -> println("A")
		Test.B -> println("B")
		Test.C -> println("C")
		Test.D -> println("D")
	}
}