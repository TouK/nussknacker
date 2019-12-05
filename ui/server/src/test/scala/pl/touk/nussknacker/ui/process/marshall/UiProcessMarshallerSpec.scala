package pl.touk.nussknacker.ui.process.marshall

import io.circe.Printer
import io.circe.parser.parse
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes
import pl.touk.nussknacker.engine.graph.node.UserDefinedAdditionalNodeFields
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

class UiProcessMarshallerSpec extends FlatSpec with Matchers {

  val someProcessDescription = "process description"
  val someNodeDescription = "single node description"
  val processWithPartialAdditionalFields =
    s"""
       |{
       |    "metaData" : { "id": "custom", "typeSpecificData": {"type": "StreamMetaData", "parallelism" : 2, "splitStateToDisk" : true }, "additionalFields": {"description": "$someProcessDescription"}},
       |    "exceptionHandlerRef" : { "parameters" : [ { "name": "errorsTopic", "expression": { "language": "spel", "expression": "error.topic" }}]},
       |    "nodes" : [
       |        {
       |            "type" : "Source",
       |            "id" : "start",
       |            "ref" : { "typ": "kafka-transaction", "parameters": [ { "name": "topic", "expression": { "language": "spel", "expression": "in.topic" }}]},
       |            "additionalFields": { "description": "$someNodeDescription"}
       |        }
       |    ],"additionalBranches":[]
       |}
      """.stripMargin

  val processWithFullAdditionalFields =
    s"""
       |{
       |    "metaData" : { "id": "custom", "typeSpecificData": {"type": "StreamMetaData", "parallelism" : 2, "splitStateToDisk" : true }, "isSubprocess": false, "subprocessVersions": {}, "additionalFields": { "description": "$someProcessDescription", "groups": [], "properties": {}} },
       |    "exceptionHandlerRef" : { "parameters" : [ { "name": "errorsTopic", "expression": { "language": "spel", "expression": "error.topic" }}]},
       |    "nodes" : [
       |        {
       |            "type" : "Source",
       |            "id" : "start",
       |            "ref" : { "typ": "kafka-transaction", "parameters": [ { "name": "topic", "expression": { "language": "spel", "expression": "in.topic" }}]},
       |            "additionalFields": { "description": "$someNodeDescription"}
       |        }
       |    ],"additionalBranches":[]
       |}
      """.stripMargin

  val processWithoutAdditionalFields =
    s"""
       |{
       |    "metaData" : { "id": "custom", "typeSpecificData": {"type": "StreamMetaData", "parallelism" : 2}},
       |    "exceptionHandlerRef" : { "parameters" : [ { "name": "errorsTopic", "expression": { "language": "spel", "expression": "error.topic" }}]},
       |    "nodes" : [
       |        {
       |            "type" : "Source",
       |            "id" : "start",
       |            "ref" : { "typ": "kafka-transaction", "parameters": [ { "name": "topic", "expression": { "language": "spel", "expression": "in.topic" }}]}
       |        }
       |    ]
       |}
      """.stripMargin


  it should "unmarshall to displayable process properly" in {
    val displayableProcess = ProcessConverter.toDisplayableOrDie(processWithPartialAdditionalFields, TestProcessingTypes.Streaming)

    val processDescription = displayableProcess.properties.additionalFields.flatMap(_.description)
    val nodeDescription = displayableProcess.nodes.head.additionalFields.flatMap(_.description)
    processDescription shouldBe Some(someProcessDescription)
    nodeDescription shouldBe Some(someNodeDescription)
  }

  it should "marshall and unmarshall process" in {
    val baseProcess = processWithFullAdditionalFields
    val displayableProcess = ProcessConverter.toDisplayableOrDie(baseProcess, TestProcessingTypes.Streaming)
    val canonical = ProcessConverter.fromDisplayable(displayableProcess)

    //TODO: set dropNullKeys as default (some util?)
    val processAfterMarshallAndUnmarshall = Printer.noSpaces.copy(dropNullValues = true).pretty(ProcessMarshaller.toJson(canonical))

    parse(processAfterMarshallAndUnmarshall) shouldBe parse(baseProcess)
  }

  it should "unmarshall json without additional fields" in {
    val displayableProcess = ProcessConverter.toDisplayableOrDie(processWithoutAdditionalFields, TestProcessingTypes.Streaming)

    displayableProcess.id shouldBe "custom"
    displayableProcess.nodes.head.additionalFields shouldBe None
    displayableProcess.properties.additionalFields shouldBe None
  }
}
