package pl.touk.nussknacker.engine.api.definition

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.NodeId

class ParameterValidatorSpec extends AnyFunSuite with TableDrivenPropertyChecks with Matchers {

  private val nodeId = NodeId("someNode")

  test("NotBlankParameterValidator") {
    forAll(Table(
      ("inputExpression", "isValid"),
      ("''", false),
      (" '' ", false),
      ("\"\"", false),
      (" \"\" ", false),
      ("'someString' ", true),
      ("\"someString\" ", true),
      ("\"someString\" + \"\"", true)
    )) { (expression, expected) =>
      NotBlankParameterValidator.isValid("dummy", expression, None)(nodeId).isValid shouldBe expected
    }
  }

  test("CustomExpressionParameterValidator") {
    forAll(Table(
      ("validationExpression", "expectedValueType", "inputExpression", "isValid"),
      ("#param > 10", "Number", "-14", false),
      ("#param > 10", "Number", "14", true),
      ("#param.toLowerCase() == \"left\" || #param.toLowerCase() == \"right\"", "String", "\"lEfT\"", true),
      ("#param.toLowerCase() == \"left\" || #param.toLowerCase() == \"right\"", "String", "\"forwards\"", false),
      // TODO more tests
    )) { (validationExpression, expectedValueType,inputExpression, isValid) =>
      CustomExpressionParameterValidator(validationExpression, expectedValueType).isValid("param", inputExpression, None)(nodeId).isValid shouldBe isValid
    }
  }

}
