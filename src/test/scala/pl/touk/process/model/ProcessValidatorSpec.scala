package pl.touk.process.model

import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FlatSpec, Matchers}
import graph.ProcessBuilder

class ProcessValidatorSpec extends FlatSpec with Matchers {

  it should "validated with success" in {
    val correctProcess = ProcessBuilder.start("id1").end("id2")
    ProcessValidator.validate(correctProcess) should matchPattern {
      case Valid(_) =>
    }
  }

  it should "find duplicated ids" in {
    val duplicatedId = "id1"
    val processWithDuplicatedIds = ProcessBuilder.start(duplicatedId).end(duplicatedId)
    ProcessValidator.validate(processWithDuplicatedIds) should matchPattern {
      case Invalid(errors) =>
    }
  }

}