package pl.touk.nussknacker.engine.livedata

import org.scalatest.funsuite.AnyFunSuiteLike
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class SlidingWindowCounterSpec extends AnyFunSuiteLike with Matchers {

  private val startTime =
    Instant.parse("2025-04-06T13:18:00Z")

  test("return counts when 1 event per second added for each event type") {
    implicit val mutableClock: MutableClock = new MutableClock(startTime)
    val counter                             = new SlidingWindowCounter[Int](startTime, 10)

    1 to 10 foreach { _ =>
      mutableClock.advanceBySeconds(1)
      1 to 10 foreach { i =>
        counter.add(i)
      }
    }

    counter.getThroughput.toSet shouldBe Set(
      1  -> BigDecimal(1),
      2  -> BigDecimal(1),
      3  -> BigDecimal(1),
      4  -> BigDecimal(1),
      5  -> BigDecimal(1),
      6  -> BigDecimal(1),
      7  -> BigDecimal(1),
      8  -> BigDecimal(1),
      9  -> BigDecimal(1),
      10 -> BigDecimal(1),
    )
  }

  test("throughput is correctly calculated for single event during consecutive seconds") {
    implicit val mutableClock: MutableClock = new MutableClock(startTime)
    val counter                             = new SlidingWindowCounter[Int](startTime, 10)

    // Empty result before event is received
    counter.getThroughput.toSet shouldBe Set.empty

    // Calculated as 1 event per second in the first second
    counter.add(0)
    counter.getThroughput.toSet shouldBe Set(0 -> BigDecimal(1))

    mutableClock.advanceBySeconds(1)
    // Calculated as 0.5000 event per second when another second passes
    counter.getThroughput.toSet shouldBe Set(0 -> BigDecimal(0.5))

    mutableClock.advanceBySeconds(2)
    // Calculated as 0.2500 event in fifth second
    counter.getThroughput.toSet shouldBe Set(0 -> BigDecimal(0.25))

    mutableClock.advanceBySeconds(6)
    // Calculated as 0.1000 event in eleventh second
    counter.getThroughput.toSet shouldBe Set(0 -> BigDecimal(0.1))

    mutableClock.advanceBySeconds(1)
    // Empty result, because the event moved outside the time window
    counter.getThroughput.toSet shouldBe Set.empty
  }

}
