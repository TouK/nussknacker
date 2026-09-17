package pl.touk.nussknacker.engine.spel

import pl.touk.nussknacker.engine.api.typed.{TypingConfiguration, TypingConfigurationProvider}

import java.util.concurrent.atomic.AtomicReference

private[engine] object MutableTypingConfigurationProvider extends TypingConfigurationProvider {

  private val currentConfig: AtomicReference[TypingConfiguration] =
    new AtomicReference[TypingConfiguration](TypingConfiguration.default)

  override def config: TypingConfiguration = {
    currentConfig.get()
  }

  def set(typingConfiguration: TypingConfiguration): Unit = {
    currentConfig.set(typingConfiguration)
  }

  def reset(): Unit = {
    currentConfig.set(TypingConfiguration.default)
  }

  def withStrictUnknownAssignment[T](run: => T): T = {
    set(TypingConfiguration(allowUnknownToAnyAssignment = false))
    try run
    finally reset()
  }

}

final class MutableTypingConfigurationProviderWrapper extends TypingConfigurationProvider {
  override def config: TypingConfiguration = MutableTypingConfigurationProvider.config
}
