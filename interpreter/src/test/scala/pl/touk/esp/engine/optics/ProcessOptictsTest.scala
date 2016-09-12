package pl.touk.esp.engine.optics

import org.scalatest.{FlatSpec, Matchers, OptionValues}
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.canonicalgraph.canonicalnode.VariableBuilder
import pl.touk.esp.engine.canonize.ProcessCanonizer

class ProcessOptictsTest extends FlatSpec with OptionValues with Matchers {

  import Implicits._
  import pl.touk.esp.engine.spel.Implicits._

  it should "modify nested node" in {
    val buildVarNodeId = "buildVarId"
    val varName = "varName"

    val negativeGraph =
      GraphBuilder
        .buildVariable(buildVarNodeId, varName)
        .sink("negativeSinkId", "sinkType")

    val process =
      EspProcessBuilder
        .id("processId")
        .exceptionHandler()
        .source("sourceId", "sourceType")
        .filter("filterId", "1 < 2", Some(negativeGraph))
        .sink("positiveSinkId", "sinkType")

    val canonical = ProcessCanonizer.canonize(process)

    val newVarName = "newVarName"

    canonical.select[VariableBuilder](buildVarNodeId).value.varName shouldEqual varName
    val modificationResult = canonical.modify[VariableBuilder](buildVarNodeId)(_.copy(varName = newVarName))
    modificationResult.value.select[VariableBuilder](buildVarNodeId).value.varName shouldEqual newVarName
    modificationResult.modifiedCount shouldBe 1
  }

}
