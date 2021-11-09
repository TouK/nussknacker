package pl.touk.nussknacker.engine.marshall

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import io.circe.{Codec, Json}
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FlatSpec, Inside, Matchers, OptionValues}
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, _}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.InvalidTailOfBranch
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.api.CirceUtil._

class ProcessMarshallerSpec extends FlatSpec with Matchers with OptionValues with Inside with TableDrivenPropertyChecks {

  import spel.Implicits._

  it should "marshall and unmarshall to same scenario" in {

    def nestedGraph(id: String) =
      GraphBuilder
        .processor(id + "Processor", id + "Service")
        .emptySink(id + "End", "")

    val process =
      EspProcessBuilder
        .id("process1")
        .source("a", "")
        .filter("b", "alamakota == 'true'", nestedGraph("b"))
        .customNode("b", "alamakota == 'true'", "someRef")
        .buildVariable("c", "fooVar", "f1" -> "expr1", "f2" -> "expr2")
        .enricher("d", "barVar", "dService", "p1" -> "expr3")
        .switch("f", "expr4", "eVar", nestedGraph("e"), Case("e1", GraphBuilder.emptySink("endE1", "")))

    val result = marshallAndUnmarshall(process)

    result should equal(Some(process))
  }

  it should "marshall and unmarshall to same scenario with ending processor" in {
    val process = EspProcessBuilder
            .id("process1")
            .source("a", "")
            .processorEnd("d", "dService", "p1" -> "expr3")

    val result = marshallAndUnmarshall(process)

    result should equal(Some(process))
  }

  it should "marshall and unmarshall to same scenario with ending custom node" in {
    val process = EspProcessBuilder
      .id("process1")
      .source("a", "")
      .endingCustomNode("d", None, "endingCustomNode", "p1" -> "expr3")

    val result = marshallAndUnmarshall(process)

    result should equal(Some(process))
  }

  it should "marshall and unmarshall to same scenario with additional fields" in {
    val processAdditionalFields = Table(
      "processAditionalFields",
      ProcessAdditionalFields(description = Some("process description"), properties = Map("customProperty" -> "customPropertyValue")),
      ProcessAdditionalFields(description = None, properties = Map("customProperty" -> "customPropertyValue")),
      ProcessAdditionalFields(description = Some("process description"), properties = Map("customProperty" -> "customPropertyValue")),
      ProcessAdditionalFields(description = Some("process description"), properties = Map.empty),
      ProcessAdditionalFields(description = None, properties = Map.empty)
    )

    forAll(processAdditionalFields) { additionalFields =>
      val process = EspProcessBuilder
        .id("process1")
        .additionalFields(additionalFields.description, additionalFields.properties)
        .source("a", "")
        .processorEnd("d", "dService", "p1" -> "expr3")

      val result = marshallAndUnmarshall(process)

      result should equal(Some(process))
    }
  }

  it should "unmarshall with known process additional fields" in {
    val marshalledAndUnmarshalledFields = Table(
      ("marshalled", "unmarshalled"),
      ("""{ "description" : "process description", "properties" : { "customProperty" : "customPropertyValue" } }""",
        ProcessAdditionalFields(description = Some("process description"), properties = Map("customProperty" -> "customPropertyValue"))),
      ("""{ "properties" : { "customProperty" : "customPropertyValue" } }""",
        ProcessAdditionalFields(description = None, properties = Map("customProperty" -> "customPropertyValue"))),
      ("""{ "description" : "process description", "properties" : { "customProperty" : "customPropertyValue" } }""",
        ProcessAdditionalFields(description = Some("process description"), properties = Map("customProperty" -> "customPropertyValue"))),
      ("""{ "description" : "process description" }""",
        ProcessAdditionalFields(description = Some("process description"), properties = Map.empty)),
      ("""{ "description" : "process description", "properties": {} }""",
        ProcessAdditionalFields(description = Some("process description"), properties = Map.empty))
    )

    forAll(marshalledAndUnmarshalledFields) { (marshalled: String, unmarshaled: ProcessAdditionalFields) =>
      val processJson = buildProcessJsonWithAdditionalFields(processAdditionalFields = Some(marshalled))

      inside(ProcessMarshaller.fromJson(processJson)) { case Valid(process) =>
        process.metaData.id shouldBe "custom"
        process.metaData.additionalFields shouldBe Some(unmarshaled)
      }
    }
  }


  it should "unmarshall with known node additional fields" in {
    val processJson = buildProcessJsonWithAdditionalFields(nodeAdditionalFields = Some("""{ "description": "single node description"}"""))

    inside(ProcessMarshaller.fromJson(processJson)) { case Valid(process) =>
      process.metaData.id shouldBe "custom"
      process.nodes should have size 1
      process.nodes.head.data.additionalFields shouldBe Some(UserDefinedAdditionalNodeFields(description = Some("single node description"), None))
    }
  }

  it should "unmarshall with missing additional fields" in {
    val processJson = buildProcessJsonWithAdditionalFields()

    inside(ProcessMarshaller.fromJson(processJson)) { case Valid(process) =>
      process.metaData.id shouldBe "custom"
      process.metaData.additionalFields shouldBe None
      process.nodes.head.data.additionalFields shouldBe None
    }
  }

  // TODO: There is no way to create a node with additional fields.

  it should "unmarshall and omit custom additional fields" in {
    val processJson = buildProcessJsonWithAdditionalFields(processAdditionalFields = Some("""{ "custom" : "value" }"""), nodeAdditionalFields = Some("""{ "custom": "value" }"""))

    inside(ProcessMarshaller.fromJson(processJson)) { case Valid(process) =>
      process.metaData.id shouldBe "custom"
      process.metaData.additionalFields shouldBe Some(ProcessAdditionalFields(description = None, properties = Map.empty))
      process.nodes should have size 1
      process.nodes.head.data.additionalFields shouldBe Some(UserDefinedAdditionalNodeFields(description = None, None))
    }
  }

  it should "detect bad branch" in {

    def checkOneInvalid(expectedBadNodeId: String, nodes: CanonicalNode*) = {
      inside(ProcessCanonizer.uncanonize(CanonicalProcess(MetaData("1", StreamMetaData()), nodes.toList, List.empty))) {
        case Invalid(NonEmptyList(InvalidTailOfBranch(id), Nil)) => id shouldBe expectedBadNodeId
      }
    }
    val source = FlatNode(Source("s1", SourceRef("a", List())))

    checkOneInvalid("filter", source, canonicalnode.FilterNode(Filter("filter", Expression("", "")), List()))
    checkOneInvalid("split", source, canonicalnode.SplitNode(Split("split"), List.empty))
    checkOneInvalid("switch", source, canonicalnode.SwitchNode(Switch("switch", Expression("", ""), ""), List.empty, List.empty))
  }

  it should "handle legacy endResult" in {
    val nodeDataCodec: Codec[NodeData] = deriveConfiguredCodec

    val oldFormat = Json.obj(
      "id" -> Json.fromString("t1"),
      "type" -> Json.fromString("Sink"),
      "ref" -> Json.obj("typ" -> Json.fromString("t2") , "parameters" -> Json.arr()),
      "endResult" -> Json.obj("language" -> Json.fromString("spel"), "expression" -> Json.fromString("#someInput"))
    )
    val nodeData = nodeDataCodec.decodeJson(oldFormat).fold(k => throw new IllegalArgumentException(k), identity)
    nodeData.asInstanceOf[Sink].legacyEndResultExpression shouldBe Some(Expression("spel", "#someInput"))

    nodeDataCodec(nodeData).deepDropNullValues shouldBe oldFormat
  }

  private def marshallAndUnmarshall(process: EspProcess): Option[EspProcess] = {
    val marshalled = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2
    val unmarshalled = ProcessMarshaller.fromJson(marshalled).toOption
    unmarshalled.foreach(_ shouldBe ProcessCanonizer.canonize(process))
    ProcessCanonizer.uncanonize(unmarshalled.value).toOption
  }

  private def buildProcessJsonWithAdditionalFields(processAdditionalFields: Option[String] = None, nodeAdditionalFields: Option[String] = None) =
    s"""
      |{
      |    "metaData" : {
      |        "id": "custom",
      |         "typeSpecificData": { "type" : "StreamMetaData", "parallelism" : 2 }
      |         ${processAdditionalFields.map(fields => s""", "additionalFields" : $fields""").getOrElse("")}
      |    },
      |    "exceptionHandlerRef" : { "parameters" : [ { "name": "errorsTopic", "expression": { "language": "spel", "expression": "error.topic" }}]},
      |    "nodes" : [
      |        {
      |            "type" : "Source",
      |            "id" : "start",
      |            "ref" : { "typ": "kafka-transaction", "parameters": [ { "name": "topic", "expression": { "language": "spel", "expression": "in.topic" }}]}
      |            ${nodeAdditionalFields.map(fields => s""", "additionalFields" : $fields""").getOrElse("")}
      |        }
      |    ]
      |}
    """.stripMargin
}
