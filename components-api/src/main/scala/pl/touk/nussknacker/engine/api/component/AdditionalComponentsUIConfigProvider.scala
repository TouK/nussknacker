package pl.touk.nussknacker.engine.api.component

import pl.touk.nussknacker.engine.api.component.AdditionalComponentsUIConfigProvider.SingleComponentConfigWithoutId

/**
  * The current implementation allows providing configs only for standard components - meaning that fragments and base components aren't handled
  */
trait AdditionalComponentsUIConfigProvider extends Serializable {

  def getAllForProcessingType(processingType: String): Map[ComponentId, SingleComponentConfigWithoutId]

}

object AdditionalComponentsUIConfigProvider {
  val empty = new DefaultAdditionalComponentsUIConfigProvider(Map.empty)

  case class SingleComponentConfigWithoutId(
      params: Option[Map[String, ParameterConfig]],
      icon: Option[String],
      docsUrl: Option[String],
      componentGroup: Option[ComponentGroupName],
      disabled: Boolean = false
  ) {

    def toSingleComponentConfig: SingleComponentConfig = SingleComponentConfig(
      params = params,
      icon = icon,
      docsUrl = docsUrl,
      disabled = disabled,
      componentGroup = componentGroup,
      componentId = None
    )

  }

  object SingleComponentConfigWithoutId {
    val zero: SingleComponentConfigWithoutId = SingleComponentConfigWithoutId(None, None, None, None)
  }

}
