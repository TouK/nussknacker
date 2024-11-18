package pl.touk.nussknacker.engine.management.periodic.util

import java.util.UUID

// This util generates UUID for given Long value.
// The Long -> UUID mapping is deterministic and does not change, as long as the seedBytes are the same
object DeterministicUUIDFromLong {

  private val seedBytes: Array[Byte] = Array(119, -29, 31, -68, 44, -126, -89, 11, 97, 87, 54, -47, 39, -73, 28, 101)

  def longUUID(long: Long): UUID = {
    val idBytes = padToEightBytes(BigInt(long).toByteArray)
    if (seedBytes != null) {
      for (i <- 0 until 8) {
        idBytes(i) = (idBytes(i) ^ seedBytes(i)).toByte
      }
      for (i <- 8 until 16) {
        idBytes(i - 8) = (idBytes(i - 8) ^ seedBytes(i)).toByte
      }
    }
    UUID.nameUUIDFromBytes(idBytes)
  }

  private def padToEightBytes(arr: Array[Byte]): Array[Byte] = {
    val result = Array.fill[Byte](8)(0)
    Array.copy(arr, 0, result, 0, arr.length) // Copy original array into the new array
    result
  }

}
