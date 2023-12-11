package pl.touk.nussknacker.engine.compile

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{Source, TestWithParametersSupport}
import pl.touk.nussknacker.engine.definition.fragment.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition

class StubbedFragmentInputTestSource(
    fragmentInputDefinition: FragmentInputDefinition,
    fragmentDefinitionExtractor: FragmentComponentDefinitionExtractor
) {

  def createSource(): Source with TestWithParametersSupport[Any] = {
    new Source with TestWithParametersSupport[Any] {
      override def testParametersDefinition: List[Parameter] = {
        fragmentDefinitionExtractor.extractParametersDefinition(fragmentInputDefinition).value
      }

      override def parametersToTestData(params: Map[String, AnyRef]): Any = params
    }
  }

}
