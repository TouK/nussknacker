package pl.touk.esp.engine.optics

import org.scalatest.{FlatSpec, Matchers, OptionValues}
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.canonicalgraph.canonicalnode.VariableBuilder
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.EspProcess

class ProcessOptictsTest extends FlatSpec with OptionValues with Matchers {

  import pl.touk.esp.engine.spel.Implicits._
  import Implicits._

  it should "modify nested node" in {
    val buildVarNodeId = "buildVarId"
    val varName = "varName"

    val negativeGraph =
      GraphBuilder
        .buildVariable(buildVarNodeId, varName)
        .sink("negativeSinkId", "sinkType")

    val graph =
      GraphBuilder
        .source("sourceId", "sourceType")
        .filter("filterId", "1 < 2", Some(negativeGraph))
        .sink("positiveSinkId", "sinkType")

    val process = EspProcess(MetaData("processId"), graph)

    val canonical = ProcessCanonizer.canonize(process)

    val newVarName = "newVarName"

    canonical.select[VariableBuilder](buildVarNodeId).value.varName shouldEqual varName
    val modificationResult = canonical.modify[VariableBuilder](buildVarNodeId)(_.copy(varName = newVarName))
    modificationResult.value.select[VariableBuilder](buildVarNodeId).value.varName shouldEqual newVarName
    modificationResult.modifiedCount shouldBe 1
  }

}
