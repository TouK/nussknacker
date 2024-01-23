package pl.touk.nussknacker.engine.definition.fragment

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultComponentConfigDeterminer
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker,
  ComponentStaticDefinition,
  FragmentSpecificData
}

object FragmentComponentDefinition {

  def apply(
      implementationInvoker: ComponentImplementationInvoker,
      parameters: List[Parameter],
      outputNames: List[String],
      docsUrl: Option[String],
      componentId: ComponentId
  ): ComponentDefinitionWithImplementation = {
    val uiDefinition = DefaultComponentConfigDeterminer.forFragment(componentId, docsUrl)
    // Currently fragments are represented as method-based component, probably we change it to some dedicated type
    MethodBasedComponentDefinitionWithImplementation(
      implementationInvoker,
      null,
      FragmentSpecificData(outputNames),
      ComponentStaticDefinition(
        parameters,
        Some(Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[String], Unknown)))
      ),
      uiDefinition
    )
  }

}
