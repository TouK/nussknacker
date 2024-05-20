package pl.touk.nussknacker.engine.util.functions

import pl.touk.nussknacker.engine.api.{Documentation, HideToString}

import java.util.concurrent.ThreadLocalRandom

object random extends RandomUtils

trait RandomUtils extends HideToString {

  @Documentation(description =
    "Returns an uniformly distributed double value between 0.0 (inclusive) and 1.0 (exclusive)"
  )
  def nextDouble: Double =
    ThreadLocalRandom.current().nextDouble()

  @Documentation(description = "Returns an uniformly distributed double value between fromInclusive and toExclusive")
  def nextDouble(fromInclusive: Double, toExclusive: Double): Double =
    ThreadLocalRandom.current().nextDouble(fromInclusive, toExclusive)

  @Documentation(description = "Returns an uniformly distributed double value between 0.0 and toExclusive")
  def nextDouble(toExclusive: Double): Double =
    ThreadLocalRandom.current().nextDouble(toExclusive)

  @Documentation(description = "Returns an uniformly distributed long value between fromInclusive and toExclusive")
  def nextLong(fromInclusive: Long, toExclusive: Long): Long =
    ThreadLocalRandom.current().nextLong(fromInclusive, toExclusive)

  @Documentation(description = "Returns an uniformly distributed long value between 0 and toExclusive")
  def nextLong(toExclusive: Long): Long =
    ThreadLocalRandom.current().nextLong(toExclusive)

  @Documentation(description = "Returns an uniformly distributed integer value between fromInclusive and toExclusive")
  def nextInt(fromInclusive: Int, toExclusive: Int): Int =
    ThreadLocalRandom.current().nextInt(fromInclusive, toExclusive)

  @Documentation(description = "Returns an uniformly distributed integer value between 0 and toExclusive")
  def nextInt(toExclusive: Int): Int =
    ThreadLocalRandom.current().nextInt(toExclusive)

  @Documentation(description = "Returns an uniformly distributed boolean value")
  def nextBoolean: Boolean =
    ThreadLocalRandom.current().nextBoolean()

  @Documentation(description =
    "Returns a boolean value with a success rate determined by the given parameter. Success rate should be between 0.0 and 1.0. For 0.0 always returns false and for 1.0 always returns true"
  )
  def nextBooleanWithSuccessRate(successRate: Double): Boolean =
    nextDouble < successRate

}
