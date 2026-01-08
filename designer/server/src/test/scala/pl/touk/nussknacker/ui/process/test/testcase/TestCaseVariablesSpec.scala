package pl.touk.nussknacker.ui.process.test.testcase

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionTestUtils
import pl.touk.nussknacker.ui.process.test.testcase

class TestCaseVariablesSpec extends AnyFunSuite with Matchers {

  test("should extend class definition set with TESTS helper") {
    val originalClassDefinitionSet = ClassDefinitionTestUtils.createDefinitionWithDefaultsAndExtensions

    val classDefinitionSet =
      TestCaseVariables.extendClassDefinitionSet(originalClassDefinitionSet, ClassDefinitionTestUtils.DefaultSettings)

    val newClasses = classDefinitionSet.classDefinitionsMap -- originalClassDefinitionSet.classDefinitionsMap.keySet
    val originalClasses = classDefinitionSet.classDefinitionsMap -- newClasses.keySet

    newClasses.keySet shouldBe Set(
      classOf[testcase.tests.type],
      classOf[testcase.AssertionResult],
    )
    originalClasses shouldBe originalClassDefinitionSet.classDefinitionsMap
  }

}
