package pl.touk.nussknacker.engine.util

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.util.TimestampUtils.normalizeTimestampToMillis

class TimestampUtilsSpec extends AnyFunSuite with Matchers {

  test("normalize timestamps properly") {
    normalizeTimestampToMillis(31535999999L) shouldBe 31535999999000L //  1 ms before 1971 should be treated as seconds and multiplied by 1000
    normalizeTimestampToMillis(1693389673000L) shouldBe 1693389673000L // 2023-08-30T10:01:13 should be treated as milliseconds and left unchanged
    normalizeTimestampToMillis(1693389673000L) shouldBe normalizeTimestampToMillis(1693389673L) // 2023-08-30T10:01:13 in seconds and in milliseconds should normalize to same value
  }
}
