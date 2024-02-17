package pl.touk.nussknacker.engine.definition.fragment

import cats.data.Validated
import pl.touk.nussknacker.engine.api.component.{
  ComponentGroupName,
  ComponentId,
  ComponentType,
  DesignerWideComponentId
}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.{ComponentDefinitionWithLogic, ComponentLogic}

class FragmentComponentDefinitionExtractor(
    classLoader: ClassLoader,
    translateGroupName: ComponentGroupName => Option[ComponentGroupName],
    determineDesignerWideId: ComponentId => DesignerWideComponentId
) {

  val parametersExtractor = new FragmentParametersDefinitionExtractor(classLoader)

  def extractFragmentComponentDefinition(
      fragment: CanonicalProcess,
  ): Validated[FragmentDefinitionError, ComponentDefinitionWithLogic] = {
    FragmentGraphDefinitionExtractor.extractFragmentGraph(fragment).map { case (input, _, outputs) =>
      val parameters =
        parametersExtractor.extractFragmentParametersDefinition(input.parameters)(NodeId(input.id)).value
      val outputNames = outputs.map(_.name).sorted
      val docsUrl     = fragment.metaData.typeSpecificData.asInstanceOf[FragmentSpecificData].docsUrl
      val componentId = determineDesignerWideId(ComponentId(ComponentType.Fragment, fragment.name.value))

      FragmentComponentDefinition(
        name = fragment.name.value,
        componentLogic = ComponentLogic.nullReturningComponentLogic,
        parameters = parameters,
        outputNames = outputNames,
        docsUrl = docsUrl,
        translateGroupName = translateGroupName,
        designerWideId = componentId,
      )
    }
  }

}
