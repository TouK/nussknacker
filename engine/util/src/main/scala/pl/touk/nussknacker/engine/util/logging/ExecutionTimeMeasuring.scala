package pl.touk.nussknacker.engine.util.logging

import com.typesafe.scalalogging.LazyLogging

trait ExecutionTimeMeasuring { self: LazyLogging =>

  def measure[R](additionalMessage: => String)(block: => R): R = {
    val t0 = System.nanoTime()
    val result = block
    val t1 = System.nanoTime()
    logger.debug(s"Elapsed time during $additionalMessage: ${ (t1 - t0) / 1000 }us")
    result
  }

}
