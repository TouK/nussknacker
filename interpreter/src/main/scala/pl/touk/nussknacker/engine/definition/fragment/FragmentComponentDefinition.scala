package pl.touk.nussknacker.engine.definition.fragment

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.{ComponentStaticDefinition, FragmentSpecificData}

object FragmentComponentDefinition {

  def apply(
      parameters: List[Parameter],
      outputNames: List[String],
      docsUrl: Option[String]
  ): ComponentStaticDefinition = {
    val config = DefaultComponentConfigDeterminer.forFragment(docsUrl)
    ComponentStaticDefinition(
      parameters,
      Some(Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Unknown))),
      config,
      config.componentGroupUnsafe,
      FragmentSpecificData(outputNames)
    )
  }

}
