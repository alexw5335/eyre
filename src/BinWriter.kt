package eyre

import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.math.max
import kotlin.math.min

@Suppress("Unused", "MemberVisibilityCanBePrivate")
class BinWriter(bytes: ByteArray) {


	constructor(initialSize: Int) : this(ByteArray(initialSize))

	constructor() : this(8192)



	companion object {
		fun write(path: String, block: (BinWriter) -> Unit) {
			val writer = BinWriter()
			block(writer)
			Files.write(Paths.get(path), writer.copy())
		}
	}



	var bytes = bytes; private set

	var pos = 0

	fun copy() = bytes.copyOf(pos)

	fun copy(count: Int) = bytes.copyOf(count)

	fun copy(pos: Int, count: Int) = bytes.copyOfRange(pos, pos + count)

	val isEmpty get() = pos == 0

	val isNotEmpty get() = pos > 0



	/*
	Position
	 */



	fun reset() {
		pos = 0
	}
	
	fun clear() {
		pos = 0
		Arrays.fill(bytes, 0)
	}
	
	inline fun at(newPos: Int, block: () -> Unit) {
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
	
	fun align2() = if(pos and 1 != 0) i8(0) else Unit

	fun align(alignment: Int) {
		pos = (pos + alignment - 1) and -alignment
		ensureCapacity()
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

	fun i24(value: Int) {
		ensureCapacity(4)
		Unsafe.instance.putInt(bytes, pos + 16L, value)
		pos += 3
	}

	fun i32(value: Int) {
		ensureCapacity(4)
		Unsafe.instance.putInt(bytes, pos + 16L, value)
		pos += 4
	}

	fun i40(value: Long) {
		ensureCapacity(8)
		Unsafe.instance.putLong(bytes, pos + 16L, value)
		pos += 5
	}

	fun i48(value: Long) {
		ensureCapacity(8)
		Unsafe.instance.putLong(bytes, pos + 16L, value)
		pos += 6
	}

	fun i56(value: Long) {
		ensureCapacity(8)
		Unsafe.instance.putLong(bytes, pos + 16L, value)
		pos += 7
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
	fun i32(value: Long) = i32(value.toUInt().toInt())




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


	fun bytes(pos: Int, writer: BinWriter, srcPos: Int = 0, length: Int = writer.pos) {
		System.arraycopy(writer.bytes, srcPos, bytes, pos, length)
	}

	fun bytes(writer: BinWriter, srcPos: Int = 0, length: Int = writer.pos) {
		ensureCapacity(length)
		System.arraycopy(writer.bytes, srcPos, bytes, pos, length)
		pos += length
	}

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
		for(i in 0 ..< min(8, string.length))
			i8(string[i].code)
		for(i in 0 ..< max(0, 8 - string.length))
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


}