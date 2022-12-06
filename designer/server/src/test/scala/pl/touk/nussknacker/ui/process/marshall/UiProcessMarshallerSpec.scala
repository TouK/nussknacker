package pl.touk.nussknacker.ui.process.marshall

import io.circe.parser.parse
import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.api.helpers.{TestCategories, TestProcessingTypes}
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.CirceUtil.humanReadablePrinter

class UiProcessMarshallerSpec extends AnyFlatSpec with Matchers {

  val someProcessDescription = "scenario description"
  val someNodeDescription = "single node description"
  val processWithPartialAdditionalFields: Json = parse(
    s"""
       |{
       |    "metaData" : { "id": "custom", "typeSpecificData": {"type": "StreamMetaData", "parallelism" : 2, "spillStateToDisk" : true }, "additionalFields": {"description": "$someProcessDescription"}},
       |    "nodes" : [
       |        {
       |            "type" : "Source",
       |            "id" : "start",
       |            "ref" : { "typ": "kafka-transaction", "parameters": [ { "name": "topic", "expression": { "language": "spel", "expression": "in.topic" }}]},
       |            "additionalFields": { "description": "$someNodeDescription"}
       |        }
       |    ],"additionalBranches":[]
       |}
      """.stripMargin).fold(throw _, identity)

  val processWithFullAdditionalFields: Json = parse(
    s"""
       |{
       |    "metaData" : { "id": "custom", "typeSpecificData": {"type": "StreamMetaData", "parallelism" : 2, "spillStateToDisk" : true }, "subprocessVersions": {}, "additionalFields": { "description": "$someProcessDescription", "properties": {}} },
       |    "nodes" : [
       |        {
       |            "type" : "Source",
       |            "id" : "start",
       |            "ref" : { "typ": "kafka-transaction", "parameters": [ { "name": "topic", "expression": { "language": "spel", "expression": "in.topic" }}]},
       |            "additionalFields": { "description": "$someNodeDescription"}
       |        }
       |    ],"additionalBranches":[]
       |}
      """.stripMargin).fold(throw _, identity)

  val processWithoutAdditionalFields: Json = parse(
    s"""
       |{
       |    "metaData" : { "id": "custom", "typeSpecificData": {"type": "StreamMetaData", "parallelism" : 2}},
       |    "nodes" : [
       |        {
       |            "type" : "Source",
       |            "id" : "start",
       |            "ref" : { "typ": "kafka-transaction", "parameters": [ { "name": "topic", "expression": { "language": "spel", "expression": "in.topic" }}]}
       |        }
       |    ]
       |}
      """.stripMargin).fold(throw _, identity)

  it should "unmarshall to displayable scenario properly" in {
    val displayableProcess = ProcessConverter.toDisplayableOrDie(ProcessMarshaller.fromJsonUnsafe(processWithPartialAdditionalFields), TestProcessingTypes.Streaming, TestCategories.Category1)

    val processDescription = displayableProcess.properties.additionalFields.flatMap(_.description)
    val nodeDescription = displayableProcess.nodes.head.additionalFields.flatMap(_.description)
    processDescription shouldBe Some(someProcessDescription)
    nodeDescription shouldBe Some(someNodeDescription)
  }

  it should "marshall and unmarshall scenario" in {
    val baseProcess = processWithFullAdditionalFields
    val displayableProcess = ProcessConverter.toDisplayableOrDie(ProcessMarshaller.fromJsonUnsafe(baseProcess), TestProcessingTypes.Streaming, TestCategories.Category1)
    val canonical = ProcessConverter.fromDisplayable(displayableProcess)

    val processAfterMarshallAndUnmarshall = canonical.asJson.printWith(humanReadablePrinter)

    parse(processAfterMarshallAndUnmarshall).right.get shouldBe baseProcess
  }

  it should "unmarshall json without additional fields" in {
    val displayableProcess = ProcessConverter.toDisplayableOrDie(ProcessMarshaller.fromJsonUnsafe(processWithoutAdditionalFields), TestProcessingTypes.Streaming, TestCategories.Category1)

    displayableProcess.id shouldBe "custom"
    displayableProcess.nodes.head.additionalFields shouldBe None
    displayableProcess.properties.additionalFields shouldBe None
  }
}
