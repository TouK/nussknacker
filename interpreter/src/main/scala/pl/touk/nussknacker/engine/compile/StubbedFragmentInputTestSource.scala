package pl.touk.nussknacker.engine.compile

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{Source, TestWithParametersSupport}
import pl.touk.nussknacker.engine.definition.fragment.FragmentCompleteDefinitionExtractor
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition

class StubbedFragmentInputTestSource(
    fragmentInputDefinition: FragmentInputDefinition,
    fragmentDefinitionExtractor: FragmentCompleteDefinitionExtractor
) {

  def createSource(validationContext: ValidationContext): Source with TestWithParametersSupport[Any] = {
    new Source with TestWithParametersSupport[Any] {
      override def testParametersDefinition: List[Parameter] = {
        fragmentDefinitionExtractor
          .extractParametersDefinition(fragmentInputDefinition, validationContext)(NodeId(fragmentInputDefinition.id))
          .value
      }

      override def parametersToTestData(params: Map[String, AnyRef]): Any = params
    }
  }

}
