package pl.touk.nussknacker.test.base.it

import java.time.Clock

trait WithClock {
  def clock: Clock = Clock.systemUTC()
}
