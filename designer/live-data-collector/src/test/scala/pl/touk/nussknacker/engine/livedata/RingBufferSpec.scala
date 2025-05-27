package pl.touk.nussknacker.engine.livedata

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

class RingBufferSpec extends AnyFunSuiteLike with Matchers {

  test("create and update value for a single key") {
    val ringBuffer = new RingBuffer[String, Int](10)
    ringBuffer.update("first", _ => 1)
    ringBuffer.values shouldBe List(1)
    ringBuffer.update("first", previousValue => previousValue.get + 2)
    ringBuffer.values shouldBe List(3)
  }

  test("create and update values for multiple keys") {
    val ringBuffer = new RingBuffer[String, Int](10)
    ringBuffer.update("first", _ => 1)
    ringBuffer.values shouldBe List(1)
    ringBuffer.update("second", _ => 5)
    ringBuffer.values shouldBe List(1, 5)
    ringBuffer.update("first", previousValue => previousValue.get + 2)
    ringBuffer.values shouldBe List(3, 5)
    ringBuffer.update("second", previousValue => previousValue.get - 2)
    ringBuffer.values shouldBe List(3, 3)
  }

  test("evict oldest value") {
    val ringBuffer = new RingBuffer[String, Int](10)
    1 to 10 foreach { i =>
      ringBuffer.update(s"$i", _ => i)
    }
    ringBuffer.values shouldBe (1 to 10).toList
    ringBuffer.update("11", _ => 11)
    ringBuffer.values shouldBe (2 to 11).toList
    ringBuffer.update("12", _ => 12)
    ringBuffer.values shouldBe (3 to 12).toList
    ringBuffer.update("12", _ => 13)
    ringBuffer.values shouldBe List(3, 4, 5, 6, 7, 8, 9, 10, 11, 13)
  }

}
