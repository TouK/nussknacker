package pl.touk.nussknacker.engine.api.definition

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.NodeId

class ParameterValidatorSpec extends AnyFunSuite with TableDrivenPropertyChecks with Matchers {

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
      val nodeId = NodeId("someNode")
      NotBlankParameterValidator.isValid("dummy", expression, None)(nodeId).isValid shouldBe expected
    }
  }

}
