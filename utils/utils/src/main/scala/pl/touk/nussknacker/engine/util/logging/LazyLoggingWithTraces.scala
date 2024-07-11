package pl.touk.nussknacker.engine.util.logging

import com.typesafe.scalalogging.{LazyLogging, Logger}

//sometimes we want to log on high level (e.g. WARN/ERROR) to find them easily but show stacktraces only at DEBUG level
trait LazyLoggingWithTraces extends LazyLogging {

  implicit class LoggerExtension(logger: Logger) {

    def warnWithDebugStack(msg: => String, ex: Throwable): Unit = {
      if (logger.underlying.isDebugEnabled()) {
        logger.debug(msg, ex)
      } else {
        logger.warn(msg)
      }
    }

    def debugWithTraceStack(msg: => String, ex: Throwable): Unit = {
      if (logger.underlying.isTraceEnabled()) {
        logger.trace(msg, ex)
      } else {
        logger.debug(msg)
      }
    }

  }

}
