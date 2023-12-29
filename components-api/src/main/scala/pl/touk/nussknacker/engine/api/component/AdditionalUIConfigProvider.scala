package pl.touk.nussknacker.engine.api.component

import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider.SingleComponentConfigWithoutId

/**
 * Trait allowing the provision of UI configuration for components and scenario properties, without requiring a model reload.
 *
 * TODO: The current implementation allows providing configs only for standard components - meaning that fragments and built-in components aren't handled.
 */
trait AdditionalUIConfigProvider extends Serializable {

  def getAllForProcessingType(processingType: String): Map[ComponentId, SingleComponentConfigWithoutId]

  def getScenarioPropertiesUIConfigs(processingType: String): Map[String, ScenarioPropertyConfig]

}

object AdditionalUIConfigProvider {
  val empty = new DefaultAdditionalUIConfigProvider(Map.empty, Map.empty)

  case class SingleComponentConfigWithoutId(
      params: Map[String, ParameterConfig],
      icon: Option[String],
      docsUrl: Option[String],
      componentGroup: Option[ComponentGroupName],
      disabled: Boolean = false
  ) {

    def toSingleComponentConfig: SingleComponentConfig = SingleComponentConfig(
      params = Some(params),
      icon = icon,
      docsUrl = docsUrl,
      disabled = disabled,
      componentGroup = componentGroup,
      componentId = None
    )

  }

  object SingleComponentConfigWithoutId {
    val zero: SingleComponentConfigWithoutId = SingleComponentConfigWithoutId(Map.empty, None, None, None)
  }

}
