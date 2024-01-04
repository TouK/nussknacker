package pl.touk.nussknacker.engine.definition.fragment

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.{ComponentStaticDefinition, FragmentSpecificData}

// TODO: Replace by just StaticComponentDefinition after StaticComponentDefinition.category removal
class FragmentComponentDefinition(
    parameters: List[Parameter],
    outputNames: List[String],
    docsUrl: Option[String]
) {

  def toStaticDefinition(category: String): ComponentStaticDefinition =
    FragmentComponentDefinition.prepareStaticDefinition(parameters, Some(List(category)), docsUrl, outputNames)

}

object FragmentComponentDefinition {

  def prepareStaticDefinition(
      parameters: List[Parameter],
      categories: Option[List[String]],
      docsUrl: Option[String],
      outputNames: List[String]
  ): ComponentStaticDefinition =
    ComponentStaticDefinition(
      parameters,
      Some(Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Unknown))),
      categories,
      DefaultComponentConfigDeterminer.forFragment(docsUrl),
      FragmentSpecificData(outputNames)
    )

}
