package pl.touk.nussknacker.engine.marshall

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import io.circe.syntax._
import io.circe.{Codec, Json}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Inside, OptionValues}
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.CirceUtil._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.{CanonicalNode, FlatNode}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.source.SourceRef

class ProcessMarshallerSpec
    extends AnyFlatSpec
    with Matchers
    with OptionValues
    with Inside
    with TableDrivenPropertyChecks {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private lazy val testStreamMetaData = StreamMetaData(Some(2), Some(true))

  it should "marshall and unmarshall to same scenario" in {

    def nestedGraph(id: String) =
      GraphBuilder
        .processor(id + "Processor", id + "Service")
        .emptySink(id + "End", "")

    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("a", "")
        .filter("b", "alamakota == 'true'".spel, nestedGraph("b"))
        .customNode("b", "alamakota == 'true'", "someRef")
        .buildVariable("c", "fooVar", "f1" -> "expr1".spel, "f2" -> "expr2".spel)
        .enricher("d", "barVar", "dService", "p1" -> "expr3".spel)
        .switch("f", "expr4".spel, "eVar", nestedGraph("e"), Case("e1".spel, GraphBuilder.emptySink("endE1", "")))

    val result = marshallAndUnmarshall(process)

    result should equal(process)
  }

  it should "marshall and unmarshall to same scenario with ending processor" in {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("a", "")
      .processorEnd("d", "dService", "p1" -> "expr3".spel)

    val result = marshallAndUnmarshall(process)

    result should equal(process)
  }

  it should "marshall and unmarshall to same scenario with ending custom node" in {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("a", "")
      .endingCustomNode("d", None, "endingCustomNode", "p1" -> "expr3".spel)

    val result = marshallAndUnmarshall(process)

    result should equal(process)
  }

  it should "marshall and unmarshall to same scenario with additional fields" in {
    val processAdditionalFields = Table(
      "processAditionalFields",
      ProcessAdditionalFields(
        description = Some("process description"),
        properties = Map("customProperty" -> "customPropertyValue"),
        StreamMetaData.typeName
      ),
      ProcessAdditionalFields(
        description = None,
        properties = Map("customProperty" -> "customPropertyValue"),
        StreamMetaData.typeName
      ),
      ProcessAdditionalFields(
        description = Some("process description"),
        properties = Map("customProperty" -> "customPropertyValue"),
        StreamMetaData.typeName
      ),
      ProcessAdditionalFields(
        description = Some("process description"),
        properties = Map.empty,
        StreamMetaData.typeName
      ),
      ProcessAdditionalFields(description = None, properties = Map.empty, StreamMetaData.typeName)
    )

    forAll(processAdditionalFields) { additionalFields =>
      val process = ScenarioBuilder
        .streaming("process1")
        .additionalFields(additionalFields.description, additionalFields.properties)
        .source("a", "")
        .processorEnd("d", "dService", "p1" -> "expr3".spel)

      val result = marshallAndUnmarshall(process)

      result should equal(process)
    }
  }

  it should "unmarshall with known process additional fields" in {
    val marshalledAndUnmarshalledFields = Table(
      ("marshalled", "unmarshalled"),
      (
        """{ "description" : "process description", "properties" : { "customProperty" : "customPropertyValue" } }""",
        ProcessAdditionalFields(
          description = Some("process description"),
          properties = Map("customProperty" -> "customPropertyValue"),
          StreamMetaData.typeName
        )
      ),
      (
        """{ "properties" : { "customProperty" : "customPropertyValue" } }""",
        ProcessAdditionalFields(
          description = None,
          properties = Map("customProperty" -> "customPropertyValue"),
          StreamMetaData.typeName
        )
      ),
      (
        """{ "description" : "process description", "properties" : { "customProperty" : "customPropertyValue" } }""",
        ProcessAdditionalFields(
          description = Some("process description"),
          properties = Map("customProperty" -> "customPropertyValue"),
          StreamMetaData.typeName
        )
      ),
      (
        """{ "description" : "process description" }""",
        ProcessAdditionalFields(
          description = Some("process description"),
          properties = Map.empty,
          StreamMetaData.typeName
        )
      ),
      (
        """{ "description" : "process description", "properties": {} }""",
        ProcessAdditionalFields(
          description = Some("process description"),
          properties = Map.empty,
          StreamMetaData.typeName
        )
      )
    )

    forAll(marshalledAndUnmarshalledFields) { (marshalled: String, unmarshaled: ProcessAdditionalFields) =>
      val canonicalProcess = buildProcessJsonWithAdditionalFields(processAdditionalFields = Some(marshalled))

      inside(canonicalProcess) { case Valid(process) =>
        process.name.value shouldBe "custom"
        process.metaData.additionalFields.description shouldBe unmarshaled.description
        process.metaData.additionalFields.metaDataType shouldBe unmarshaled.metaDataType
        unmarshaled.properties.toSet.subsetOf(process.metaData.additionalFields.properties.toSet) shouldBe true
      }
    }
  }

  it should "unmarshall with known node additional fields" in {
    val canonicalProcess = buildProcessJsonWithAdditionalFields(nodeAdditionalFields =
      Some("""{ "description": "single node description"}""")
    )

    inside(canonicalProcess) { case Valid(process) =>
      process.name.value shouldBe "custom"
      process.nodes should have size 1
      process.nodes.head.data.additionalFields shouldBe Some(
        UserDefinedAdditionalNodeFields(description = Some("single node description"), None)
      )
    }
  }

  it should "unmarshall with missing additional fields" in {
    val canonicalProcess = buildProcessJsonWithAdditionalFields()

    inside(canonicalProcess) { case Valid(process) =>
      process.name.value shouldBe "custom"
      process.metaData.additionalFields shouldBe ProcessAdditionalFields(
        None,
        testStreamMetaData.toMap,
        StreamMetaData.typeName
      )
      process.nodes.head.data.additionalFields shouldBe None
    }
  }

  // TODO: There is no way to create a node with additional fields.

  it should "unmarshall and omit custom additional fields" in {
    val canonicalProcess = buildProcessJsonWithAdditionalFields(
      processAdditionalFields = Some("""{ "custom" : "value" }"""),
      nodeAdditionalFields = Some("""{ "custom": "value" }""")
    )

    inside(canonicalProcess) { case Valid(process) =>
      process.name.value shouldBe "custom"
      process.metaData.additionalFields shouldBe ProcessAdditionalFields(
        None,
        testStreamMetaData.toMap,
        StreamMetaData.typeName
      )
      process.nodes should have size 1
      process.nodes.head.data.additionalFields shouldBe Some(UserDefinedAdditionalNodeFields(description = None, None))
    }
  }

  it should "detect bad branch" in {

    def checkOneInvalid(expectedBadNodeId: String, nodes: CanonicalNode*) = {
      inside(ProcessCanonizer.uncanonize(CanonicalProcess(MetaData("1", StreamMetaData()), nodes.toList, List.empty))) {
        case Invalid(NonEmptyList(canonize.InvalidTailOfBranch(id), Nil)) => id shouldBe expectedBadNodeId
      }
    }
    val source = FlatNode(Source("s1", SourceRef("a", List())))

    checkOneInvalid("filter", source, canonicalnode.FilterNode(Filter("filter", Expression(Language.Spel, "")), List()))
    checkOneInvalid("split", source, canonicalnode.SplitNode(Split("split"), List.empty))
    checkOneInvalid("switch", source, canonicalnode.SwitchNode(Switch("switch"), List.empty, List.empty))
  }

  it should "handle legacy endResult" in {
    val nodeDataCodec: Codec[NodeData] = deriveConfiguredCodec

    val oldFormat = Json.obj(
      "id"        -> Json.fromString("t1"),
      "type"      -> Json.fromString("Sink"),
      "ref"       -> Json.obj("typ" -> Json.fromString("t2"), "parameters" -> Json.arr()),
      "endResult" -> Json.obj("language" -> Json.fromString("spel"), "expression" -> Json.fromString("#someInput"))
    )
    val nodeData = nodeDataCodec.decodeJson(oldFormat).fold(k => throw new IllegalArgumentException(k), identity)
    nodeData.asInstanceOf[Sink].legacyEndResultExpression shouldBe Some(Expression.spel("#someInput"))

    nodeDataCodec(nodeData).deepDropNullValues shouldBe oldFormat
  }

  private def marshallAndUnmarshall(process: CanonicalProcess): CanonicalProcess = {
    val unmarshalled = ProcessMarshaller.fromJson(process.asJson).toOption
    unmarshalled.foreach(_ shouldBe process)
    unmarshalled.value
  }

  private def buildProcessJsonWithAdditionalFields(
      processAdditionalFields: Option[String] = None,
      nodeAdditionalFields: Option[String] = None
  ) =
    ProcessMarshaller.fromJson(s"""
      |{
      |    "metaData" : {
      |        "id": "custom",
      |         "typeSpecificData": { "type" : "StreamMetaData", "parallelism" : 2, "spillStateToDisk": true }
      |         ${processAdditionalFields.map(fields => s""", "additionalFields" : $fields""").getOrElse("")}
      |    },
      |    "nodes" : [
      |        {
      |            "type" : "Source",
      |            "id" : "start",
      |            "ref" : { "typ": "kafka-transaction", "parameters": [ { "name": "Topic", "expression": { "language": "spel", "expression": "in.topic" }}]}
      |            ${nodeAdditionalFields.map(fields => s""", "additionalFields" : $fields""").getOrElse("")}
      |        }
      |    ]
      |}
    """.stripMargin)

}
