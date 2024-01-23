package pl.touk.nussknacker.engine.definition.fragment

import cats.data.Validated
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  ComponentImplementationInvoker,
  ComponentWithStaticDefinition
}

class FragmentComponentDefinitionExtractor(classLoader: ClassLoader, componentInfoToId: ComponentInfo => ComponentId) {

  val parametersExtractor = new FragmentParametersWithoutValidatorsDefinitionExtractor(classLoader)

  def extractFragmentComponentDefinition(
      fragment: CanonicalProcess,
  ): Validated[FragmentDefinitionError, ComponentDefinitionWithImplementation] = {
    FragmentGraphDefinitionExtractor.extractFragmentGraph(fragment).map { case (input, _, outputs) =>
      val parameters =
        parametersExtractor.extractFragmentParametersDefinition(input.parameters)(NodeId(input.id)).value
      val outputNames = outputs.map(_.name).sorted
      val docsUrl     = fragment.metaData.typeSpecificData.asInstanceOf[FragmentSpecificData].docsUrl
      val componentId = componentInfoToId(ComponentInfo(ComponentType.Fragment, fragment.name.value))

      FragmentComponentDefinition(
        implementationInvoker = ComponentImplementationInvoker.nullImplementationInvoker,
        parameters = parameters,
        outputNames = outputNames,
        docsUrl = docsUrl,
        componentId = componentId,
      )
    }
  }

}
