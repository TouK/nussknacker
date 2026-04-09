package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.{
  ClassDefinitionDiscovery,
  ClassDefinitionExtractor,
  ClassDefinitionSet
}
import pl.touk.nussknacker.engine.definition.globalvariables.ObjectWithType
import pl.touk.nussknacker.engine.extension.ExtensionMethods

object TestCaseVariables {

  final val TestsGlobalVariableName         = "TESTS"
  final val RecordsNodeVariableName         = "records"
  final val OutgoingRecordsNodeVariableName = "outgoingRecords"

  def extendClassDefinitionSet(
      classDefinitionSet: ClassDefinitionSet,
      classExtractionSettings: ClassExtractionSettings
  ): ClassDefinitionSet = {
    val testCaseGlobalVarsTypes = Set(testsGlobalVariableType)
    val testCaseGlobalVarsDefinitions = ClassDefinitionSet(
      new ClassDefinitionDiscovery(new ClassDefinitionExtractor(classExtractionSettings))
        .discoverClassesFromTypes(testCaseGlobalVarsTypes)
    )
    classDefinitionSet.concatOnlyNewClasses(testCaseGlobalVarsDefinitions)
  }

  def extendNodeVariablesValidationContext(
      validationContext: ValidationContext,
      variableTypes: Map[String, TypingResult],
      outputVariableTypes: Map[String, TypingResult]
  ): ValidationContext = {
    val base = validationContext.withVariablesUnsafe(
      TestsGlobalVariableName -> testsGlobalVariableType,
      RecordsNodeVariableName -> recordsNodeVariableType(variableTypes),
    )
    if (outputVariableTypes.nonEmpty)
      base.withVariableUnsafe(OutgoingRecordsNodeVariableName, recordsNodeVariableType(outputVariableTypes))
    else
      base
  }

  def getNodeVariablesTyping(
      inputVariablesForNode: Map[String, TypingResult],
      outputVariableTypes: Map[String, TypingResult]
  ): Map[String, TypingResult] = {
    val base = Map(
      TestsGlobalVariableName -> testsGlobalVariableType,
      RecordsNodeVariableName -> recordsNodeVariableType(inputVariablesForNode),
    )
    if (outputVariableTypes.nonEmpty)
      base + (OutgoingRecordsNodeVariableName -> recordsNodeVariableType(outputVariableTypes))
    else
      base
  }

  def extendGlobalVariables(globalVariables: Map[String, ObjectWithType]): Map[String, ObjectWithType] =
    globalVariables +
      (TestsGlobalVariableName -> ObjectWithType(tests, testsGlobalVariableType))

  private def testsGlobalVariableType: typing.TypingResult = Typed.fromInstance(tests)

  private def recordsNodeVariableType(variablesForNode: Map[String, TypingResult]): typing.TypingResult =
    Typed.genericTypeClass(
      classOf[java.util.List[_]],
      List(Typed.record(variablesForNode))
    )

}
