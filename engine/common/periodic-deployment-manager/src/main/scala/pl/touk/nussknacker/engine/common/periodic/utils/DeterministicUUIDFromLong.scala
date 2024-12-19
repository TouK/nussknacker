package pl.touk.nussknacker.engine.common.periodic.utils

import java.util.UUID

// This util generates UUID for given Long value.
object DeterministicUUIDFromLong {

  // Seed bytes are the base of Long -> UUID transformation.
  // The mapping is deterministic and does not change, as long as the seedBytes are the same
  private val seedBytes: Array[Byte] = {
    val bytes = Array[Byte](119, -29, 31, -68, 44, -126, -89, 11, 97, 87, 54, -47, 39, -73, 28, 101)
    require(bytes.length == 16, "Seed bytes must be exactly 16 bytes long")
    bytes
  }

  def longUUID(long: Long): UUID = {
    require(long >= 0, "Input value must be non-negative")
    val idBytes = BigInt(long).toByteArray.padTo(8, 0.toByte)
    for (i <- seedBytes.indices) {
      val targetIndex = i % 8
      idBytes(targetIndex) = (idBytes(targetIndex) ^ seedBytes(i)).toByte
    }
    UUID.nameUUIDFromBytes(idBytes)
  }

}
