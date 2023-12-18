package pl.touk.nussknacker.engine.definition.fragment

import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.component.{ComponentStaticDefinition, FragmentSpecificData}

class FragmentComponentDefinition(
    parameters: List[Parameter],
    outputNames: List[String]
) {

  def toStaticDefinition(category: String, docsUrl: Option[String]): FragmentStaticDefinition = {
    val componentDefinition = ComponentStaticDefinition(
      parameters,
      Some(Typed[java.util.Map[String, Any]]),
      Some(List(category)),
      SingleComponentConfig.zero.copy(docsUrl = docsUrl),
      FragmentSpecificData
    )
    FragmentStaticDefinition(componentDefinition, outputNames)
  }

}

final case class FragmentStaticDefinition(
    componentDefinition: ComponentStaticDefinition,
    outputNames: List[String]
)
