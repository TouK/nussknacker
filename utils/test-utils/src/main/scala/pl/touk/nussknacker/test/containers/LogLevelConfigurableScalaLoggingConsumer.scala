package pl.touk.nussknacker.test.containers

import com.typesafe.scalalogging.LazyLogging
import org.testcontainers.containers.output.{BaseConsumer, OutputFrame}
import org.testcontainers.containers.output.OutputFrame.OutputType
import pl.touk.nussknacker.test.containers.LogLevelConfigurableScalaLoggingConsumer.LoggerLevel

class LogLevelConfigurableScalaLoggingConsumer(stdoutLogLevel: LoggerLevel, stderrLogLevel: LoggerLevel)
    extends BaseConsumer[LogLevelConfigurableScalaLoggingConsumer]
    with LazyLogging {

  private var prefix: String = ""

  def withPrefix(prefix: String): LogLevelConfigurableScalaLoggingConsumer = {
    this.prefix = "[" + prefix + "] "
    this
  }

  override def accept(outputFrame: OutputFrame): Unit = {
    val outputType: OutputFrame.OutputType = outputFrame.getType
    val utf8String: String                 = outputFrame.getUtf8StringWithoutLineEnding
    outputType match {
      case OutputType.END    =>
      case OutputType.STDOUT => log(stdoutLogLevel, utf8String)
      case OutputType.STDERR => log(stderrLogLevel, utf8String)
      case _                 => throw new IllegalArgumentException("Unexpected outputType " + outputType)
    }
  }

  private def log(level: LoggerLevel, msg: String): Unit = {
    def formatPrefix = if (prefix.isEmpty) "" else prefix + ": "

    level match {
      case LoggerLevel.Info  => logger.info("{}{}", formatPrefix, msg)
      case LoggerLevel.Debug => logger.debug("{}{}", formatPrefix, msg)
      case LoggerLevel.Trace => logger.trace("{}{}", formatPrefix, msg)
      case LoggerLevel.Error => logger.error("{}{}", formatPrefix, msg)
      case LoggerLevel.Warn  => logger.warn("{}{}", formatPrefix, msg)
    }
  }

}

object LogLevelConfigurableScalaLoggingConsumer {

  sealed trait LoggerLevel

  object LoggerLevel {
    case object Info  extends LoggerLevel
    case object Debug extends LoggerLevel
    case object Trace extends LoggerLevel
    case object Error extends LoggerLevel
    case object Warn  extends LoggerLevel
  }

}
