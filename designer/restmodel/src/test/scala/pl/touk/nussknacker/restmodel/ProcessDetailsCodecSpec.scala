package pl.touk.nussknacker.restmodel

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails

import java.time.Instant

class ProcessDetailsCodecSpec extends AnyFunSuite with Matchers {

  private val isFragment: Boolean = true

  private def baseProcessDetailsWithSubprocess(isFragmentFieldName: String): String =
    s"""{
      |  "id" : "My process",
      |  "name" : "My process",
      |  "processId" : 8791186787473528092,
      |  "processVersionId" : 1,
      |  "isLatestVersion" : true,
      |  "description" : "My fancy description",
      |  "isArchived" : false,
      |  "processingType" : "streaming",
      |  "processCategory" : "Category1",
      |  "modificationDate" : "2023-08-07T10:57:33.986223Z",
      |  "modifiedAt" : "2023-08-07T10:57:33.986227Z",
      |  "modifiedBy" : "user1",
      |  "createdAt" : "2023-08-07T10:57:33.986230Z",
      |  "createdBy" : "user1",
      |  "tags" : [],
      |  "json" : {
      |    "id" : "My process",
      |    "properties" : {
      |      "isFragment" : false,
      |      "additionalFields" : {
      |        "properties" : {
      |          "parallelism" : "",
      |          "spillStateToDisk" : "true",
      |          "useAsyncInterpretation" : "",
      |          "checkpointIntervalInSeconds" : ""
      |        },
      |        "metaDataType" : "StreamMetaData"
      |      }
      |    },
      |    "nodes" : [],
      |    "edges" : [],
      |    "processingType" : "streaming",
      |    "category" : "Category1"
      |  },
      |  "history" : [],
      |  "$isFragmentFieldName" : $isFragment
      |}""".stripMargin

  private def compareIsFragmentField(json: Json): Unit = {
    val baseProcessDetails = json.as[BaseProcessDetails[DisplayableProcess]].toOption
    baseProcessDetails shouldBe Symbol("defined")
    baseProcessDetails.get.isFragment shouldBe isFragment
  }

  test("decode BaseProcessDetails with isSubprocess") {
    val validJson = parse(baseProcessDetailsWithSubprocess("isSubprocess")).toOption.get
    compareIsFragmentField(validJson)
  }

  test("decode BaseProcessDetails with isFragment") {
    val validJson = parse(baseProcessDetailsWithSubprocess("isFragment")).toOption.get
    compareIsFragmentField(validJson)
  }

  test("encode BaseProcessDetails to json object with isSubprocess field") {
    val baseProcessDetails = BaseProcessDetails[DisplayableProcess](
      id = "name", name = "name", processId = ProcessId("1"), processVersionId = VersionId.initialVersionId, isLatestVersion = true,
      description = None, isArchived = false, isFragment = isFragment, processingType = "Streaming", processCategory = "category", modificationDate = Instant.now(),
      modifiedAt = Instant.now(), modifiedBy = "user1", createdAt = Instant.now(), createdBy = "user1", tags = List(), lastAction = None, lastStateAction = None, lastDeployedAction = None,
      json = DisplayableProcess(id = "name", properties = ProcessProperties(StreamMetaData(Some(1), Some(true))), nodes = List.empty, edges = List.empty, processingType = "Streaming", "Category1"),
      history = Nil, modelVersion = None
    )
    val isSubprocessField = baseProcessDetails.asJson.hcursor.downField("isSubprocess").focus
    isSubprocessField  shouldBe Symbol("defined")
    isSubprocessField.get.asBoolean.get shouldBe isFragment
  }

}
