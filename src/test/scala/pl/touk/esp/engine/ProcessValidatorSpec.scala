package pl.touk.esp.engine

import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.validate.GraphValidator

class ProcessValidatorSpec extends FlatSpec with Matchers {

  it should "validated with success" in {
    val correctProcess = GraphBuilder.start("id1").end("id2")
    GraphValidator.validate(correctProcess) should matchPattern {
      case Valid(_) =>
    }
  }

  it should "find duplicated ids" in {
    val duplicatedId = "id1"
    val processWithDuplicatedIds = GraphBuilder.start(duplicatedId).end(duplicatedId)
    GraphValidator.validate(processWithDuplicatedIds) should matchPattern {
      case Invalid(errors) =>
    }
  }

}