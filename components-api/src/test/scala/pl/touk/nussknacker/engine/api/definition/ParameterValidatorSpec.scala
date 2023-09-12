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

  test("isValidationExpressionValid") {
    forAll(Table(
      ("validationExpression", "paramName", "expectedValueType", "isValid"),
      ("#param > 10", "param", "Int", true),
      ("#param < 10", "param", "Int", true),
      ("#param + 10", "param", "Int", false),
      // TODO more tests
    )) { (validationExpression, paramName, expectedValueType, expected) =>
      CustomExpressionParameterValidator.isValidationExpressionValid(validationExpression, paramName, expectedValueType) shouldBe expected
    }
  }

  test("CustomExpressionParameterValidator") {
    forAll(Table(
      ("validationExpression", "expectedValueType", "inputExpression", "isValid"),
      ("#param > 10", "Number", "-14", false),
      ("#param > 10", "Number", "14.5", true),
      ("#param.toLowerCase() == \"left\" || #param.toLowerCase() == \"right\"", "String", "\"lEfT\"", true),
      ("#param.toLowerCase() == \"left\" || #param.toLowerCase() == \"right\"", "String", "\"forwards\"", false),
      ("#param.toLowerCase() == \"left\" || #param.toLowerCase() == \"right\"", "String", "\"forwards\"", false),
      ("#param.compareTo('2020-07-01') < 0", "Time", "'2020-07-04'", false), // these just get compared as Strings
      ("#param.compareTo(#DATE.now) < 0", "Time", "'2020-05-01'", true), // #DATE is null, lacking context
      // TODO more tests
    )) { (validationExpression, expectedValueType,inputExpression, isValid) =>
      CustomExpressionParameterValidator(validationExpression, expectedValueType).isValid("param", inputExpression, None)(nodeId).isValid shouldBe isValid
    }
  }

}
