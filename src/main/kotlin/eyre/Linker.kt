package eyre

import eyre.util.IntList
import eyre.util.NativeWriter



private const val imageBase = 0x400000L

private const val sectionAlignment = 0x1000

private const val fileAlignment = 0x200

private const val numSectionsPos = 70

private const val entryPointPos = 104

private const val imageSizePos = 144

private const val dataDirsPos = 0xC8

private const val sectionHeadersPos = 0x148

private const val symbolTablePosPos = 76

private const val numSymbolsPos = 80

private val Int.roundToFile get() = (this + fileAlignment - 1) and -fileAlignment

private val Int.roundToSection get() = (this + sectionAlignment - 1) and -sectionAlignment