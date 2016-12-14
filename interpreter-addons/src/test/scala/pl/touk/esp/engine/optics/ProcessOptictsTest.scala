package pl.touk.esp.engine.optics

import org.scalatest.{FlatSpec, Matchers, OptionValues}
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.canonicalgraph.canonicalnode.{FlatNode, FilterNode}
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node.{Filter, VariableBuilder}

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
      .filter(filterNodeId, filterExpression, negativeGraph)
      .sink("positiveSinkId", "sinkType")

  val canonical = ProcessCanonizer.canonize(process)

  it should "modify nested node" in {
    val newVarName = "newVarName"
    canonical.select(buildVarNodeId).value.data.asInstanceOf[VariableBuilder].varName shouldEqual varName
    val modificationResult = canonical.modify(buildVarNodeId)(n => FlatNode(VariableBuilder(n.id, newVarName, List.empty)))
    modificationResult.value.select(buildVarNodeId).value.data.asInstanceOf[VariableBuilder].varName shouldEqual newVarName
    modificationResult.modifiedCount shouldBe 1
  }

  it should "modify only properties without structure changes" in {
    val modifiedExpression: Expression = "1 > 2"
    val modifiedFilter = FilterNode(Filter(filterNodeId, modifiedExpression), List.empty)
    val modificationResult = canonical.modify(filterNodeId)(_ => modifiedFilter)
    modificationResult.modifiedCount shouldBe 1
    modificationResult.value.select(filterNodeId).value.data.asInstanceOf[Filter].expression shouldEqual modifiedExpression
    modificationResult.value.select(buildVarNodeId).isDefined shouldBe true
  }

}
