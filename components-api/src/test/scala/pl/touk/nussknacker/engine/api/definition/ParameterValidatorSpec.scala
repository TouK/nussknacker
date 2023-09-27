package pl.touk.nussknacker.engine.api.definition

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.typed.typing.Typed

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
      NotBlankParameterValidator.isValid("dummy", expression, None, None)(nodeId).isValid shouldBe expected
    }
  }

  test("CustomExpressionParameterValidator.isValidatorValid") {
    forAll(Table(
      ("validationExpression", "paramName", "paramType", "isValid"),
      ("#param > 10", "param", Typed[Long], true),
      ("#param < 10", "param", Typed[Long], true),
      ("#param + 10", "param", Typed[Long], false),
      // TODO more tests
    )) { (validationExpression, paramName, paramType, expected) =>
      CustomExpressionParameterValidator(validationExpression, None).isValidatorValid(paramName, paramType) shouldBe expected
    }
  }

  test("CustomExpressionParameterValidator") {
    forAll(Table(
      ("validationExpression", "paramType", "inputExpression", "isValid"),
//      ("#param > 10", Typed[Long], "-14", false),
//      ("#param > 10", Typed[Long], "14.5", true),
      ("#param.toLowerCase() == \"left\" || #param.toLowerCase() == \"right\"", Typed[String], "\"lEfT\"", true),
//      ("#param.toLowerCase() == \"left\" || #param.toLowerCase() == \"right\"", Typed[String], "\"forward\"", false),
      // TODO more tests
    )) { (validationExpression, paramType, inputExpression, isValid) =>
      CustomExpressionParameterValidator(validationExpression, None).isValid("param", inputExpression, Some(paramType), None)(nodeId).isValid shouldBe isValid
    }
  }

}
