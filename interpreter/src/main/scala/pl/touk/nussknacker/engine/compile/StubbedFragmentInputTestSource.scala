package pl.touk.nussknacker.engine.compile

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{Source, TestWithParametersSupport}

class StubbedFragmentInputTestSource(
    parameterDefinitions: List[Parameter]
) {

  def createSource(): Source with TestWithParametersSupport[Any] = {
    new Source with TestWithParametersSupport[Any] {
      override def testParametersDefinition: List[Parameter] = parameterDefinitions

      override def parametersToTestData(params: Map[String, AnyRef]): Any = params
    }
  }

}
