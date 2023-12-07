package pl.touk.nussknacker.engine.definition.fragment

import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.Output
import pl.touk.nussknacker.engine.definition.component.ComponentStaticDefinition

class FragmentComponentDefinition(
    parameters: List[Parameter],
    config: SingleComponentConfig,
    outputNames: List[String]
) {

  def toStaticDefinition(category: String): FragmentStaticDefinition = {
    val componentDefinition = ComponentStaticDefinition(
      parameters,
      Some(Typed[java.util.Map[String, Any]]),
      Some(List(category)),
      config
    )
    FragmentStaticDefinition(componentDefinition, outputNames)
  }

}

final case class FragmentStaticDefinition(
    componentDefinition: ComponentStaticDefinition,
    outputNames: List[String]
)
