package pl.touk.nussknacker.engine.api.process

/**
  * General settings for model
  * @param allowEndingScenarioWithoutSink - indicates, whether it is allowed to have nodes other than sinks as final nodes of the scenario
  */
final case class ModelSettings(
    allowEndingScenarioWithoutSink: Boolean,
)

object ModelSettings {
  val Default: ModelSettings = ModelSettings(allowEndingScenarioWithoutSink = false)
}
