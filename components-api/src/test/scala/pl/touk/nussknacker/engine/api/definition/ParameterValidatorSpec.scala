package pl.touk.nussknacker.engine.api.definition

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.NodeId

class ParameterValidatorSpec extends AnyFunSuite with TableDrivenPropertyChecks with Matchers {

  private implicit val nodeId: NodeId = NodeId("someNode")

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

  test("RegExpParameterValidator") {
    val mailValidator = RegExpParameterValidator("^[^<>]+@nussknacker\\.io$", "", "")
    val alaValidator = RegExpParameterValidator("^ala$", "", "")

    forAll(Table(
      ("inputExpression", "validator", "isValid"),
      ("''", mailValidator, false),
      ("", mailValidator, true),
      ("'lcl@nussknacker.io'", mailValidator, true),
      ("lcl@nussknacker.io", mailValidator, true),
      ("'lcl@nussknacker.io'", mailValidator, true),
      ("'lcl@nussknacker.io", mailValidator, true),
      ("lcl@nussknacker.io'", mailValidator, false),
      ("lcl@nussknacker.ios", mailValidator, false),
      ("lcl@nussknacker.ios", mailValidator, false),
      ("lcl@nussknacker.ios", LiteralParameterValidator.numberValidator, false),
      ("0", LiteralParameterValidator.numberValidator, true),
      ("'0'", LiteralParameterValidator.numberValidator, true), //FIXME: is it okay?
      ("ala", alaValidator, true),
      ("'ala'", alaValidator, true),
      ("'ala", alaValidator, false),
      ("ala'", alaValidator, false),
    )) { (expression, validator, expected) =>
      validator.isValid("dummy", expression, None)(nodeId).isValid shouldBe expected
    }
  }

}
