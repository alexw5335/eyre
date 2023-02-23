package eyre.util

import eyre.Width
import eyre.swapEndian
import java.nio.charset.Charset
import kotlin.math.max
import kotlin.math.min

@Suppress("Unused", "MemberVisibilityCanBePrivate")
class NativeWriter(bytes: ByteArray) {


	constructor(initialSize: Int) : this(ByteArray(initialSize))

	constructor() : this(8192)



	var bytes = bytes; private set

	var pos = 0

	fun getTrimmedBytes() = bytes.copyOf(pos)

	fun getTrimmedBytes(count: Int) = bytes.copyOf(count)

	fun getTrimmedBytes(pos: Int, count: Int) = bytes.copyOfRange(pos, pos + count)



	/*
	Position
	 */



	inline fun retainPos(newPos: Int, block: () -> Unit) {
		val pos = pos
		this.pos = newPos
		block()
		this.pos = pos
	}



	fun ensureCapacity() {
		if(pos >= bytes.size)
			bytes = bytes.copyOf(pos shl 2)
	}



	fun ensureCapacity(count: Int) {
		if(pos + count > bytes.size)
			bytes = bytes.copyOf((pos + count) shl 2)
	}



	fun alignEven() {
		if(pos and 1 != 0) i8(0)
	}



	fun align8() {
		zero(pos.mod(8))
	}



	fun varLengthInt(value: Int) {
		ensureCapacity(4)
		Unsafe.instance.putInt(bytes, pos + 16L, value)
		pos += ((39 - (value or 1).countLeadingZeroBits()) and -8) shr 3
	}



	fun seek(pos: Int) {
		this.pos = pos
		ensureCapacity()
	}



	/*
	Little-endian primitives
	 */



	fun i8(value: Int) {
		ensureCapacity(1)
		bytes[pos++] = value.toByte()
	}

	fun i16(value: Int) {
		ensureCapacity(2)
		Unsafe.instance.putShort(bytes, pos + 16L, value.toShort())
		pos += 2
	}

	fun i32(value: Int) {
		ensureCapacity(4)
		Unsafe.instance.putInt(bytes, pos + 16L, value)
		pos += 4
	}

	fun i64(value: Long) {
		ensureCapacity(8)
		Unsafe.instance.putLong(bytes, pos + 16L, value)
		pos += 8
	}

	fun f32(value: Float) {
		ensureCapacity(4)
		Unsafe.instance.putFloat(bytes, pos + 16L, value)
		pos += 4
	}

	fun f64(value: Double) {
		ensureCapacity(8)
		Unsafe.instance.putDouble(bytes, pos + 16L, value)
		pos += 8
	}



	fun i8(pos: Int, value: Int) = bytes.set(pos, value.toByte())

	fun i16(pos: Int, value: Int) = Unsafe.instance.putShort(bytes, pos + 16L, value.toShort())

	fun i32(pos: Int, value: Int) = Unsafe.instance.putInt(bytes, pos + 16L, value)

	fun i64(pos: Int, value: Long) = Unsafe.instance.putLong(bytes, pos + 16L, value)

	fun f32(pos: Int, value: Float) = Unsafe.instance.putFloat(bytes, pos + 16L, value)

	fun f64(pos: Int, value: Double) = Unsafe.instance.putDouble(bytes, pos + 16L, value)



	fun i24(value: Int) {
		ensureCapacity(4)
		Unsafe.instance.putInt(bytes, pos + 16L, value)
		pos += 3
	}



	/*
	Big-endian primitives
	 */



	fun i16BE(value: Int) {
		ensureCapacity(2)
		Unsafe.instance.putShort(bytes, pos + 16L, value.toShort().swapEndian)
		pos += 2
	}

	fun i32BE(value: Int) {
		ensureCapacity(4)
		Unsafe.instance.putInt(bytes, pos + 16L, value.swapEndian)
		pos += 4
	}

	fun i64BE(value: Long) {
		ensureCapacity(8)
		Unsafe.instance.putLong(bytes, pos + 16L, value.swapEndian)
		pos += 8
	}

	fun f32BE(value: Float) {
		ensureCapacity(4)
		Unsafe.instance.putInt(bytes, pos + 16L, value.toRawBits().swapEndian)
		pos += 4
	}

	fun f64BE(value: Double) {
		ensureCapacity(8)
		Unsafe.instance.putLong(bytes, pos + 16L, value.toRawBits().swapEndian)
		pos += 8
	}



	fun i16BE(pos: Int, value: Int) = Unsafe.instance.putShort(bytes, pos + 16L, value.toShort().swapEndian)

	fun i32BE(pos: Int, value: Int) = Unsafe.instance.putInt(bytes, pos + 16L, value.swapEndian)

	fun i64BE(pos: Int, value: Long) = Unsafe.instance.putLong(bytes, pos + 16L, value.swapEndian)

	fun f32BE(pos: Int, value: Float) = Unsafe.instance.putInt(bytes, pos + 16L, value.toRawBits().swapEndian)

	fun f64BE(pos: Int, value: Double) = Unsafe.instance.putLong(bytes, pos + 16L, value.toRawBits().swapEndian)



	/*
	Arrays
	 */



	fun bytes(pos: Int, array: ByteArray, srcPos: Int = 0, length: Int = array.size) {
		System.arraycopy(array, srcPos, bytes, pos, length)
	}

	fun bytes(array: ByteArray, srcPos: Int = 0, length: Int = array.size) {
		ensureCapacity(length)
		System.arraycopy(array, srcPos, bytes, pos, length)
		pos += length
	}

	fun ints(pos: Int, array: IntArray, srcPos: Int = 0, length: Int = array.size) {
		Unsafe.instance.copyMemory(array, 16L + srcPos, bytes, 16L + pos, length.toLong())
	}

	fun ints(array: IntArray, srcPos: Int = 0, length: Int = array.size) {
		ensureCapacity(length * 4)
		Unsafe.instance.copyMemory(array, 16L + srcPos, bytes, 16L + pos, length.toLong())
		pos += length * 4
	}



	/*
	Misc
	 */



	fun string(string: String, charset: Charset) {
		bytes(charset.encode(string).array())
	}



	fun ascii(string: String) {
		for(c in string) i8(c.code)
	}



	fun asciiNT(string: String) {
		for(c in string) i8(c.code)
		i8(0)
	}



	fun ascii64(string: String) {
		ensureCapacity(8)
		for(i in 0 until min(8, string.length))
			i8(string[i].code)
		for(i in 0 until max(0, 8 - string.length))
			i8(0)
	}



	fun set(count: Int, value: Int) {
		if(count <= 0) return
		ensureCapacity(count)
		Unsafe.instance.setMemory(bytes, pos + 16L, count.toLong(), value.toByte())
		pos += count
	}



	fun setTo(pos: Int, value: Int) {
		if(pos <= this.pos) return
		ensureCapacity(pos - this.pos)
		Unsafe.instance.setMemory(bytes, this.pos + 16L, pos.toLong() - this.pos, value.toByte())
		this.pos = pos
	}



	fun zero(count: Int) = set(count, 0)



	fun zeroTo(pos: Int) = setTo(pos, 0)



	fun advance(count: Int) {
		if(count <= 0) return
		ensureCapacity(count)
		pos += count
	}



	fun advanceTo(pos: Int) {
		ensureCapacity(pos - this.pos)
		this.pos = pos
	}



	/*
	Temp
	 */



	fun writeWidth(width: Width, value: Int) {
		if(value !in width) error("Value out of range")

		when(width) {
			Width.BIT8  -> i8(value)
			Width.BIT16 -> i16(value)
			Width.BIT32 -> i32(value)
			else        -> error("Value out of range")
		}
	}



	fun writeWidth(width: Width, value: Long) {
		if(value !in width) error("Value out of range")

		when(width) {
			Width.BIT8  -> i8(value.toInt())
			Width.BIT16 -> i16(value.toInt())
			Width.BIT32 -> i32(value.toInt())
			Width.BIT64 -> i64(value)
		}
	}


}