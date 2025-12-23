package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.globalvariables.{ExpressionConfigDefinition, ObjectWithType}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

class TestCaseGlobalVariablesPreparer(globalVariablesPreparer: GlobalVariablesPreparer) {

  private val TestsGlobalVariableName = "TESTS"

  def prepareValidationContextWithGlobalVariablesOnly(jobData: JobData): ValidationContext =
    globalVariablesPreparer
      .prepareValidationContextWithGlobalVariablesOnly(jobData)
      .withVariablesUnsafe(TestsGlobalVariableName -> Typed.fromInstance(tests))

  def prepareValidationContextWithGlobalVariablesOnly(scenarioPropertiesNames: Iterable[String]): ValidationContext =
    globalVariablesPreparer
      .prepareValidationContextWithGlobalVariablesOnly(scenarioPropertiesNames)
      .withVariablesUnsafe(TestsGlobalVariableName -> Typed.fromInstance(tests))

  def prepareGlobalVariables(jobData: JobData): Map[String, ObjectWithType] =
    globalVariablesPreparer.prepareGlobalVariables(jobData) +
      (TestsGlobalVariableName -> ObjectWithType(tests, Typed.fromInstance(tests)))

}

object TestCaseGlobalVariablesPreparer {
  def apply(expressionDefinition: ExpressionConfigDefinition): TestCaseGlobalVariablesPreparer =
    new TestCaseGlobalVariablesPreparer(GlobalVariablesPreparer(expressionDefinition))
}
