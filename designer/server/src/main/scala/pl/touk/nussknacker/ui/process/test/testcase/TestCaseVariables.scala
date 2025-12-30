package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.{
  ClassDefinitionDiscovery,
  ClassDefinitionExtractor,
  ClassDefinitionSet
}
import pl.touk.nussknacker.engine.definition.globalvariables.ObjectWithType
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData

object TestCaseVariables {

  final val TestsGlobalVariableName  = "TESTS"
  final val ContextsNodeVariableName = "contexts"

  def extendClassDefinitionSet(
      classDefinitionSet: ClassDefinitionSet,
      classExtractionSettings: ClassExtractionSettings
  ): ClassDefinitionSet = {
    val testCaseGlobalVarsTypes = Set(testsGlobalVariableType)
    val testCaseGlobalVarsDefinitions =
      new ClassDefinitionDiscovery(new ClassDefinitionExtractor(classExtractionSettings))
        .discoverClassesFromTypes(testCaseGlobalVarsTypes)
    classDefinitionSet.concat(testCaseGlobalVarsDefinitions)
  }

  def extendNodeVariablesValidationContext(
      validationContext: ValidationContext,
      nodeTyping: NodeTypingData
  ): ValidationContext =
    validationContext
      .withVariablesUnsafe(
        TestsGlobalVariableName -> testsGlobalVariableType,
        ContextsNodeVariableName -> Typed.genericTypeClass(
          classOf[java.util.List[_]],
          List(Typed.record(nodeTyping.variableTypes))
        ),
      )

  def extendGlobalVariables(globalVariables: Map[String, ObjectWithType]): Map[String, ObjectWithType] =
    globalVariables +
      (TestsGlobalVariableName -> ObjectWithType(tests, testsGlobalVariableType))

  private def testsGlobalVariableType: typing.TypingResult = Typed.fromInstance(tests)

}
