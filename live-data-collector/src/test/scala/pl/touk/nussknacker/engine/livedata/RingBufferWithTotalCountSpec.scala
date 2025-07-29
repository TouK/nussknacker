package pl.touk.nussknacker.engine.livedata

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

class RingBufferWithTotalCountSpec extends AnyFunSuiteLike with Matchers {

  test("create values") {
    val ringBuffer = new RingBufferWithTotalCount[Int](10)
    ringBuffer.put(1)
    ringBuffer.values shouldBe List(1)
    ringBuffer.put(5)
    ringBuffer.values shouldBe List(1, 5)
    ringBuffer.put(3)
    ringBuffer.values shouldBe List(1, 5, 3)
    ringBuffer.put(3)
    ringBuffer.values shouldBe List(1, 5, 3, 3)
    ringBuffer.totalCount shouldBe 4
  }

  test("evict oldest value, but preserve information about total count") {
    val ringBuffer = new RingBufferWithTotalCount[Int](10)
    1 to 10 foreach { i =>
      ringBuffer.put(i)
    }
    ringBuffer.values shouldBe (1 to 10).toList
    ringBuffer.put(11)
    ringBuffer.values shouldBe (2 to 11).toList
    ringBuffer.put(12)
    ringBuffer.values shouldBe (3 to 12).toList
    ringBuffer.put(13)
    ringBuffer.values shouldBe List(4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
    ringBuffer.totalCount shouldBe 13
  }

}
