package eyre

@JvmInline
value class RegValue(private val backing: Int) {
	val value get() = backing and 0b111
	val rex get()   = (backing shr 3) and 1
	val high get()  = backing shr 4
}