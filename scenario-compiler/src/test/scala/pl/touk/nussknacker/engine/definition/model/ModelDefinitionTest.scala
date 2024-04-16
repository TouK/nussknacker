package pl.touk.nussknacker.engine.definition.model

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder._

class ModelDefinitionTest extends AnyFunSuite with Matchers {

  test("should detect duplicated components") {
    an[DuplicatedComponentsException] shouldBe thrownBy {
      ModelDefinitionBuilder.empty
        .withService("foo")
        .withService("foo")
        .build
    }
  }

}
