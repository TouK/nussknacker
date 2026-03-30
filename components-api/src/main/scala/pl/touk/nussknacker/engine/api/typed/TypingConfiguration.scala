package pl.touk.nussknacker.engine.api.typed

import java.util.ServiceLoader
import scala.jdk.CollectionConverters._

// Loaded via SPI to avoid passing this config through many internal typing classes
// (e.g. AssignabilityDeterminer). Ideally it would live in ModelConfig.
final case class TypingConfiguration(allowUnknownToAnyAssignment: Boolean)

object TypingConfiguration {
  lazy val default: TypingConfiguration = TypingConfiguration(allowUnknownToAnyAssignment = true)
}

trait TypingConfigurationProvider {
  def config: TypingConfiguration
}

private[typed] object TypingConfigurationProvider {

  def load(classLoader: ClassLoader): TypingConfigurationProvider = {
    ServiceLoader
      .load(classOf[TypingConfigurationProvider], classLoader)
      .asScala
      .toList match {
      case Nil        => TypingConfigurationProvider.default
      case one :: Nil => one
      case multiple   => throw new IllegalStateException(s"Multiple TypingConfigurationProviders found: $multiple")
    }
  }

  val default: TypingConfigurationProvider = new TypingConfigurationProvider {
    override val config: TypingConfiguration = TypingConfiguration.default
  }

}
