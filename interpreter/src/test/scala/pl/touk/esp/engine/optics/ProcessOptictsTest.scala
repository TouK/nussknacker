package pl.touk.esp.engine.optics

import org.scalatest.{FlatSpec, Matchers, OptionValues}
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.canonicalgraph.canonicalnode.{Filter, VariableBuilder}
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.expression.Expression

class ProcessOptictsTest extends FlatSpec with OptionValues with Matchers {

  import Implicits._
  import pl.touk.esp.engine.spel.Implicits._

  val buildVarNodeId = "buildVarId"
  val varName = "varName"

  val negativeGraph =
    GraphBuilder
      .buildVariable(buildVarNodeId, varName)
      .sink("negativeSinkId", "sinkType")

  val filterNodeId = "filterId"
  val filterExpression: Expression = "1 < 2"

  val process =
    EspProcessBuilder
      .id("processId")
      .exceptionHandler()
      .source("sourceId", "sourceType")
      .filter(filterNodeId, filterExpression, Some(negativeGraph))
      .sink("positiveSinkId", "sinkType")

  val canonical = ProcessCanonizer.canonize(process)

  it should "modify nested node" in {
    val newVarName = "newVarName"
    canonical.select[VariableBuilder](buildVarNodeId).value.varName shouldEqual varName
    val modificationResult = canonical.modify[VariableBuilder](buildVarNodeId)(_.copy(varName = newVarName))
    modificationResult.value.select[VariableBuilder](buildVarNodeId).value.varName shouldEqual newVarName
    modificationResult.modifiedCount shouldBe 1
  }

  it should "modify only properties without structure changes" in {
    val modifiedExpression: Expression = "1 > 2"
    val modifiedFilter = Filter(filterNodeId, modifiedExpression, List.empty)
    val modificationResult = canonical.modify[Filter](filterNodeId)(_ => modifiedFilter)
    modificationResult.modifiedCount shouldBe 1
    modificationResult.value.select[Filter](filterNodeId).value.expression shouldEqual modifiedExpression
    modificationResult.value.select[VariableBuilder](buildVarNodeId).isDefined shouldBe true
  }

}
