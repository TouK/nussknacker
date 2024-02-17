package pl.touk.nussknacker.engine.definition.component.dynamic

import pl.touk.nussknacker.engine.api.component.ParameterConfig
import pl.touk.nussknacker.engine.api.context.transformation.GenericNodeTransformation
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.component.{
  ComponentRuntimeLogicFactory,
  ComponentTypeSpecificData,
  ComponentUiDefinition,
  ComponentWithRuntimeLogicFactory
}

final case class DynamicComponentWithRuntimeLogicFactory(
    override val name: String,
    override val runtimeLogicFactory: ComponentRuntimeLogicFactory,
    override val component: GenericNodeTransformation[_],
    override val componentTypeSpecificData: ComponentTypeSpecificData,
    override protected val uiDefinition: ComponentUiDefinition,
    parametersConfig: Map[String, ParameterConfig]
) extends ComponentWithRuntimeLogicFactory {

  override def withRuntimeLogicFactory(
      implementationInvoker: ComponentRuntimeLogicFactory
  ): ComponentWithRuntimeLogicFactory =
    copy(runtimeLogicFactory = implementationInvoker)

  override protected def typesFromStaticDefinition: List[TypingResult] = List.empty

}
