package pl.touk.nussknacker.engine.definition.fragment

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.{ComponentStaticDefinition, FragmentSpecificData}

object FragmentComponentDefinition {

  def apply(
      componentId: Option[ComponentId],
      parameters: List[Parameter],
      outputNames: List[String],
      docsUrl: Option[String]
  ): ComponentStaticDefinition = {
    val config = DefaultComponentConfigDeterminer.forFragment(componentId, docsUrl)
    ComponentStaticDefinition(
      parameters = parameters,
      returnType = Some(Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Unknown))),
      componentConfig = config,
      originalGroupName = config.componentGroupUnsafe,
      componentTypeSpecificData = FragmentSpecificData(outputNames)
    )
  }

}
