package pl.touk.nussknacker.test.containers

import org.slf4j.Logger
import org.testcontainers.containers.output.OutputFrame.OutputType
import org.testcontainers.containers.output.{BaseConsumer, OutputFrame}

class DebugLevelSlf4jLogConsumer(logger: Logger, var separateOutputStreams: Boolean)
    extends BaseConsumer[DebugLevelSlf4jLogConsumer] {

  private var prefix: String = ""

  def this(logger: Logger) = {
    this(logger, false)
  }

  def withPrefix(prefix: String): DebugLevelSlf4jLogConsumer = {
    this.prefix = "[" + prefix + "] "
    this
  }

  def withSeparateOutputStreams: DebugLevelSlf4jLogConsumer = {
    this.separateOutputStreams = true
    this
  }

  override def accept(outputFrame: OutputFrame): Unit = {
    val outputType: OutputFrame.OutputType = outputFrame.getType
    val utf8String: String                 = outputFrame.getUtf8StringWithoutLineEnding
    outputType match {
      case OutputType.END =>
      case OutputType.STDOUT =>
        if (separateOutputStreams)
          logger.debug("{}{}", if (prefix.isEmpty) "" else prefix + ": ", utf8String)
        else
          logger.debug("{}{}: {}", prefix, outputType, utf8String)
      case OutputType.STDERR =>
        if (separateOutputStreams)
          logger.error("{}{}", if (prefix.isEmpty) "" else prefix + ": ", utf8String)
        else
          logger.debug("{}{}: {}", prefix, outputType, utf8String)
      case _ =>
        throw new IllegalArgumentException("Unexpected outputType " + outputType)
    }
  }

}
