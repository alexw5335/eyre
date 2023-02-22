package eyre.util

import sun.misc.Unsafe

@Suppress("Unused", "MemberVisibilityCanBePrivate")
object Unsafe {

	val instance: Unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let {
		it.isAccessible = true
		it.get(null) as Unsafe
	}

	fun set(address: Long, size: Long, value: Byte) = instance.setMemory(address, size, value)

	fun set(address: Long, size: Int, value: Byte) = instance.setMemory(address, size.toLong(), value)

	fun zero(address: Long, size: Long) = instance.setMemory(address, size, 0)

	fun zero(address: Long, size: Int) = instance.setMemory(address, size.toLong(), 0)

	fun malloc(size: Long) = instance.allocateMemory(size)

	fun malloc(size: Int) = malloc(size.toLong())

	fun calloc(size: Long) = malloc(size).also { set(it, size, 0) }

	fun calloc(size: Int) = calloc(size.toLong())

	fun realloc(address: Long, size: Long) = instance.reallocateMemory(address, size)

	fun free(address: Long) = instance.freeMemory(address)

	fun copy(src: Long, dst: Long, size: Long) = instance.copyMemory(src, dst, size)



	/*
	Primitive reading
	 */



	fun getByte(address: Long)   = instance.getByte(address)

	fun getChar(address: Long)   = instance.getChar(address)

	fun getShort(address: Long)  = instance.getShort(address)

	fun getInt(address: Long)    = instance.getInt(address)

	fun getFloat(address: Long)  = instance.getFloat(address)

	fun getLong(address: Long)   = instance.getLong(address)

	fun getDouble(address: Long) = instance.getDouble(address)



	/*
	Primitive write
	 */



	fun setByte(address: Long, value: Byte)     = instance.putByte(address, value)

	fun setChar(address: Long, value: Char)     = instance.putChar(address, value)

	fun setShort(address: Long, value: Short)   = instance.putShort(address, value)

	fun setInt(address: Long, value: Int)       = instance.putInt(address, value)

	fun setFloat(address: Long, value: Float)   = instance.putFloat(address, value)

	fun setLong(address: Long, value: Long)     = instance.putLong(address, value)

	fun setDouble(address: Long, value: Double) = instance.putDouble(address, value)



	/*
	Primitive array read
	 */



	fun getBytes(address: Long, size: Int) = ByteArray(size).also {
		instance.copyMemory(null, address, it, 16L, size.toLong())
	}

	fun getChars(address: Long, size: Int) = CharArray(size).also {
		instance.copyMemory(null, address, it, 16L, size.toLong() * 2)
	}

	fun getShorts(address: Long, size: Int) = ShortArray(size).also {
		instance.copyMemory(null, address, it, 16L, size.toLong() * 2)
	}

	fun getInts(address: Long, size: Int) = IntArray(size).also {
		instance.copyMemory(null, address, it, 16L, size.toLong() * 4)
	}

	fun getFloats(address: Long, size: Int) = FloatArray(size).also {
		instance.copyMemory(null, address, it, 16L, size.toLong() * 4)
	}

	fun getLongs(address: Long, size: Int) = LongArray(size).also {
		instance.copyMemory(null, address, it, 16L, size.toLong() * 8)
	}

	fun getDoubles(address: Long, size: Int) = DoubleArray(size).also {
		instance.copyMemory(null, address, it, 16L, size.toLong() * 8)
	}



	/*
	Primitive array write
	 */



	fun setBytes(address: Long, value: ByteArray) {
		instance.copyMemory(value, 16L, null, address, value.size.toLong())
	}

	fun setChars(address: Long, value: CharArray) {
		instance.copyMemory(value, 16L, null, address, value.size.toLong() * 2)
	}

	fun setShorts(address: Long, value: ShortArray) {
		instance.copyMemory(value, 16L, null, address, value.size.toLong() * 2)
	}

	fun setInts(address: Long, value: IntArray) {
		instance.copyMemory(value, 16L, null, address, value.size.toLong() * 4)
	}

	fun setFloats(address: Long, value: FloatArray) {
		instance.copyMemory(value, 16L, null, address, value.size.toLong() * 4)
	}

	fun setLongs(address: Long, value: LongArray) {
		instance.copyMemory(value, 16L, null, address, value.size.toLong() * 8)
	}

	fun setDoubles(address: Long, value: DoubleArray) {
		instance.copyMemory(value, 16L, null, address, value.size.toLong() * 8)
	}


}