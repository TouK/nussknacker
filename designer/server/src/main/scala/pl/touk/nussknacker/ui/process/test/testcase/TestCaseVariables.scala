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
import pl.touk.nussknacker.ui.process.test.testcase

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
      nodeTyping: testcase.NodeTyping
  ): ValidationContext = {
    val outgoingRecordsType =
      if (nodeTyping.outputVariables.nonEmpty) recordsNodeVariableType(nodeTyping.outputVariables)
      else listOfUnknownType
    validationContext.withVariablesUnsafe(
      TestsGlobalVariableName         -> testsGlobalVariableType,
      RecordsNodeVariableName         -> recordsNodeVariableType(nodeTyping.inputVariables),
      OutgoingRecordsNodeVariableName -> outgoingRecordsType,
    )
  }

  def getNodeVariablesTyping(nodeTyping: testcase.NodeTyping): Map[String, TypingResult] = {
    val outgoingRecordsType =
      if (nodeTyping.outputVariables.nonEmpty) recordsNodeVariableType(nodeTyping.outputVariables)
      else listOfUnknownType
    Map(
      TestsGlobalVariableName         -> testsGlobalVariableType,
      RecordsNodeVariableName         -> recordsNodeVariableType(nodeTyping.inputVariables),
      OutgoingRecordsNodeVariableName -> outgoingRecordsType,
    )
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

  private def listOfUnknownType: typing.TypingResult =
    Typed.genericTypeClass(
      classOf[java.util.List[_]],
      List(typing.Unknown)
    )

}
