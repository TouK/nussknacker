package pl.touk.esp.engine.compile

import cats.data.Validated.{Invalid, Valid}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine._

class ProcessCompilerSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  it should "validated with success" in {
    val correctProcess = EspProcess(MetaData("process1"), GraphBuilder.source("id1", "").sink("id2", ""))
    ProcessCompiler.default.validate(correctProcess) should matchPattern {
      case Valid(_) =>
    }
  }

  it should "find duplicated ids" in {
    val duplicatedId = "id1"
    val processWithDuplicatedIds = EspProcess(MetaData("process1"), GraphBuilder.source(duplicatedId, "").sink(duplicatedId, ""))
    ProcessCompiler.default.validate(processWithDuplicatedIds) should matchPattern {
      case Invalid(errors) =>
    }
  }

  it should "find expression parse error" in {
    val processWithInvalidExpresssion = EspProcess(
      MetaData("process1"),
      GraphBuilder.source("id1", "")
        .sink("id2", "wtf!!!", "")
    )
    ProcessCompiler.default.validate(processWithInvalidExpresssion) should matchPattern {
      case Invalid(errors) =>
    }
  }

}