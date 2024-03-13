package pl.touk.nussknacker.engine.compile

import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.{Source, TestWithParametersSupport}

class FragmentSourceWithTestWithParametersSupportFactory(
    parameterDefinitions: List[Parameter]
) {

  def createSource(): Source with TestWithParametersSupport[Any] = {
    new Source with TestWithParametersSupport[Any] {
      override def testParametersDefinition: List[Parameter] = parameterDefinitions

      override def parametersToTestData(params: Map[ParameterName, AnyRef]): Any =
        params.map { case (name, value) => (name.value, value) }
    }
  }

}
