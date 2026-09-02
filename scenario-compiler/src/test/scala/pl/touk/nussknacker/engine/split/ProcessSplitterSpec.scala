package pl.touk.nussknacker.engine.split

import cats.data.NonEmptyList
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.GraphBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SourceNode
import pl.touk.nussknacker.engine.splittedgraph.part.{
  CustomNodePart,
  MultiOutputCustomNodePart,
  SingleOutputCustomNodePart,
  SplittedOutput
}
import pl.touk.nussknacker.engine.splittedgraph.splittednode

class ProcessSplitterSpec extends AnyFunSuite with Matchers with OptionValues {

  private def split(root: SourceNode): CustomNodePart = {
    val process    = EspProcess(MetaData("proc", StreamMetaData()), NonEmptyList.of(root))
    val splitted   = ProcessSplitter.split(process)
    val sourcePart = splitted.sources.head
    sourcePart.nextParts.collect { case custom: CustomNodePart => custom } match {
      case single :: Nil => single
      case other         => fail(s"Expected exactly one CustomNodePart, got: $other")
    }
  }

  private def splitMultiOutput(root: SourceNode): MultiOutputCustomNodePart =
    split(root) match {
      case multi: MultiOutputCustomNodePart => multi
      case other                            => fail(s"Expected MultiOutputCustomNodePart, got: $other")
    }

  private def output(part: MultiOutputCustomNodePart, name: String): SplittedOutput =
    part.outputs.find(_.name == name).value

  test("split single-output custom node with continuation embedded in node") {
    val customPart = split(
      GraphBuilder
        .source("source", "sourceType")
        .customNode("custom", "out", "customTransformer")
        .emptySink("sink", "sinkType")
    )

    val singleOutputPart = customPart match {
      case single: SingleOutputCustomNodePart => single
      case other                              => fail(s"Expected SingleOutputCustomNodePart, got: $other")
    }
    singleOutputPart.node should matchPattern {
      case splittednode.OneOutputSubsequentNode(_, Some(splittednode.PartRef(_))) =>
    }
    singleOutputPart.nextParts.map(_.id) shouldBe List("sink")
    singleOutputPart.ends shouldBe empty
  }

  test("split multi-output custom node with connected main output") {
    val customPart = splitMultiOutput(
      GraphBuilder
        .source("source", "sourceType")
        .customNodeWithOutputs(
          "custom",
          Some("out"),
          "customTransformer",
          List(
            "main"     -> GraphBuilder.emptySink("mainSink", "sinkType").value,
            "rejected" -> GraphBuilder.emptySink("rejectedSink", "sinkType").value
          )
        )
    )

    customPart.outputs.map(_.name).toList shouldBe List("main", "rejected")
    output(customPart, "main").nextParts.map(_.id) shouldBe List("mainSink")
    output(customPart, "rejected").nextParts.map(_.id) shouldBe List("rejectedSink")

    customPart.nextParts.map(_.id) should contain theSameElementsAs List("mainSink", "rejectedSink")
    customPart.ends shouldBe empty
  }

  test("split multi-output custom node keeps duplicate output names for the compiler to reject") {
    val customPart = splitMultiOutput(
      GraphBuilder
        .source("source", "sourceType")
        .customNodeWithOutputs(
          "custom",
          Some("out"),
          "customTransformer",
          List(
            "rejected" -> GraphBuilder.emptySink("rejectedSink1", "sinkType").value,
            "rejected" -> GraphBuilder.emptySink("rejectedSink2", "sinkType").value
          )
        )
    )

    customPart.outputs.map(_.name).toList shouldBe List("rejected", "rejected")
    customPart.nextParts.map(_.id) shouldBe List("rejectedSink1", "rejectedSink2")
    customPart.ends shouldBe empty
  }

}
