package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.{
  ClassDefinitionDiscovery,
  ClassDefinitionExtractor,
  ClassDefinitionSet
}

object TestCaseVariables {

  private val TestsGlobalVariableName = "TESTS"

  def extendClassDefinitionSet(
      classDefinitionSet: ClassDefinitionSet,
      classExtractionSettings: ClassExtractionSettings
  ): ClassDefinitionSet = {
    val testCaseGlobalVarsTypes = Set(Typed.fromInstance(tests))
    val testCaseGlobalVarsDefinitions =
      new ClassDefinitionDiscovery(new ClassDefinitionExtractor(classExtractionSettings))
        .discoverClassesFromTypes(testCaseGlobalVarsTypes)
    classDefinitionSet.concat(testCaseGlobalVarsDefinitions)
  }

}
