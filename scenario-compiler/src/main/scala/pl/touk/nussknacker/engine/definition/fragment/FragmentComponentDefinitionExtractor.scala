package pl.touk.nussknacker.engine.definition.fragment

import cats.data.Validated
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.component.{
  ComponentGroupName,
  ComponentId,
  ComponentType,
  DesignerWideComponentId
}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker
}
import pl.touk.nussknacker.engine.util.MetaDataExtractor

class FragmentComponentDefinitionExtractor(
    classLoader: ClassLoader,
    translateGroupName: ComponentGroupName => Option[ComponentGroupName],
    determineDesignerWideId: ComponentId => DesignerWideComponentId
) {

  val parametersExtractor = new FragmentParametersDefinitionExtractor(classLoader)

  def extractFragmentComponentDefinition(
      fragment: CanonicalProcess,
      allowedProcessingModes: AllowedProcessingModes
  ): Validated[FragmentDefinitionError, ComponentDefinitionWithImplementation] = {
    FragmentGraphDefinitionExtractor.extractFragmentGraph(fragment).map { case (input, _, outputs) =>
      val parameters =
        parametersExtractor.extractFragmentParametersDefinition(input.parameters)(NodeId(input.id)).value
      val outputNames = outputs.map(_.name).sorted
      val componentId = determineDesignerWideId(ComponentId(ComponentType.Fragment, fragment.name.value))
      val fragmentSpecificData = MetaDataExtractor
        .extractTypeSpecificDataOrDefault[FragmentSpecificData](fragment.metaData, FragmentSpecificData())

      FragmentComponentDefinition(
        name = fragment.name.value,
        implementationInvoker = ComponentImplementationInvoker.nullReturningComponentImplementationInvoker,
        parameters = parameters,
        outputNames = outputNames,
        docsUrl = fragmentSpecificData.docsUrl,
        componentGroupName = fragmentSpecificData.componentGroup.map(ComponentGroupName(_)),
        icon = fragmentSpecificData.icon,
        translateGroupName = translateGroupName,
        designerWideId = componentId,
        allowedProcessingModes = allowedProcessingModes
      )
    }
  }

}
