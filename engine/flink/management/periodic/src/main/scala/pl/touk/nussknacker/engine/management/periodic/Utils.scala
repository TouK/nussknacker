package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.scalalogging.LazyLogging

object Utils extends LazyLogging {

  def runSafely(action: => Unit): Unit = try {
    action
  } catch {
    case t: Throwable => logger.error("Error occurred, but skipping it", t)
  }
}
