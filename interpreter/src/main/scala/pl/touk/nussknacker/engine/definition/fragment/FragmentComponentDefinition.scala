package pl.touk.nussknacker.engine.definition.fragment

import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.{ComponentStaticDefinition, FragmentSpecificData}

object FragmentComponentDefinition {

  def apply(
      name: String,
      processingType: ProcessingType,
      parameters: List[Parameter],
      outputNames: List[String],
      docsUrl: Option[String]
  ): ComponentStaticDefinition = {
    val config = DefaultComponentConfigDeterminer.forFragment(
      Some(ComponentId.default(processingType, ComponentInfo(ComponentType.Fragment, name))),
      docsUrl
    )
    ComponentStaticDefinition(
      parameters = parameters,
      returnType = Some(Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Unknown))),
      componentConfig = config,
      originalGroupName = config.componentGroupUnsafe,
      componentTypeSpecificData = FragmentSpecificData(outputNames)
    )
  }

  // We don't want to pass input parameters definition as parameters to definition of factory creating stubbed source because
  // use them only for testParametersDefinition which are used in the runtime not in compile-time.
  def stubDefinition: ComponentStaticDefinition = {
    val config = DefaultComponentConfigDeterminer.forFragment(None, None)
    ComponentStaticDefinition(
      parameters = List.empty,
      returnType = Some(Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Unknown))),
      componentConfig = config,
      originalGroupName = config.componentGroupUnsafe,
      componentTypeSpecificData = FragmentSpecificData(List.empty)
    )
  }

}
