package pl.touk.nussknacker.ui.process.marshall

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil.humanReadablePrinter
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData

class UiProcessMarshallerSpec extends AnyFlatSpec with Matchers {

  val someProcessDescription = "scenario description"
  val someNodeDescription    = "single node description"

  val processWithoutScenarioProperties: Json = parse(s"""
       |{
       |    "metaData" : {
       |    "id" : "testId",
       |    "additionalFields" : {
       |       "description": "$someProcessDescription",
       |       "properties" : {
       |         "parallelism" : "1",
       |         "spillStateToDisk" : "true",
       |         "useAsyncInterpretation" : "",
       |         "checkpointIntervalInSeconds" : ""
       |       },
       |       "metaDataType": "StreamMetaData"
       |    }
       |  },
       |    "nodes" : [
       |        {
       |            "type" : "Source",
       |            "id" : "start",
       |            "ref" : { "typ": "kafka-transaction", "parameters": [ { "name": "Topic", "expression": { "language": "spel", "expression": "in.topic" }}]},
       |            "additionalFields": { "description": "$someNodeDescription"}
       |        }
       |    ],"additionalBranches":[]
       |}
      """.stripMargin).fold(throw _, identity)

  def processWithFullAdditionalFields(name: ProcessName): Json = parse(s"""
       |{
       |    "metaData" : {
       |    "id" : "$name",
       |    "additionalFields" : {
       |       "description": "$someProcessDescription",
       |       "properties" : {
       |         "someProperty1": "",
       |         "someProperty2": "someValue2",
       |         "parallelism" : "1",
       |         "spillStateToDisk" : "true",
       |         "useAsyncInterpretation" : "",
       |         "checkpointIntervalInSeconds" : ""
       |       },
       |       "metaDataType": "StreamMetaData"
       |    }
       |  },
       |    "nodes" : [
       |        {
       |            "type" : "Source",
       |            "id" : "start",
       |            "ref" : { "typ": "kafka-transaction", "parameters": [ { "name": "Topic", "expression": { "language": "spel", "expression": "in.topic" }}]},
       |            "additionalFields": { "description": "$someNodeDescription"}
       |        }
       |    ],"additionalBranches":[]
       |}
      """.stripMargin).fold(throw _, identity)

  it should "unmarshall to displayable scenario properly" in {
    val displayableProcess = ProcessConverter.toDisplayable(
      ProcessMarshaller.fromJsonUnsafe(processWithoutScenarioProperties)
    )

    val processDescription = displayableProcess.properties.additionalFields.description
    val nodeDescription    = displayableProcess.nodes.head.additionalFields.flatMap(_.description)
    processDescription shouldBe Some(someProcessDescription)
    nodeDescription shouldBe Some(someNodeDescription)
  }

  it should "marshall and unmarshall scenario" in {
    val baseProcess = processWithFullAdditionalFields(ProcessTestData.sampleProcessName)
    val displayableProcess = ProcessConverter.toDisplayable(
      ProcessMarshaller.fromJsonUnsafe(baseProcess)
    )
    val canonical = ProcessConverter.fromDisplayable(displayableProcess, ProcessTestData.sampleProcessName)

    val processAfterMarshallAndUnmarshall = canonical.asJson.printWith(humanReadablePrinter)

    parse(processAfterMarshallAndUnmarshall).toOption.get shouldBe baseProcess
  }

}
