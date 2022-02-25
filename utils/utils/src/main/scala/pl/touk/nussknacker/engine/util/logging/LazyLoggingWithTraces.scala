package pl.touk.nussknacker.engine.util.logging

import com.typesafe.scalalogging.LazyLogging

//sometimes we want to log on high level (e.g. WARN/ERROR) to find them easily but show stacktraces only at DEBUG level
trait LazyLoggingWithTraces extends LazyLogging {

  def infoWithDebugStack(msg: => String, ex: Throwable): Unit = {
    logger.debug(msg, ex)
    logger.info(msg)
  }

  def warnWithDebugStack(msg: => String, ex: Throwable): Unit = {
    logger.debug(msg, ex)
    logger.warn(msg)
  }

  def errorWithDebugStack(msg: => String, ex: Throwable): Unit = {
    logger.debug(msg, ex)
    logger.error(msg)
  }

}
