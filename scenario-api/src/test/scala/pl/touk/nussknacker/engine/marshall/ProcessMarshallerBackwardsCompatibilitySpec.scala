package pl.touk.nussknacker.engine.marshall

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ProcessMarshallerBackwardsCompatibilitySpec extends AnyFlatSpec with Matchers{

  val oldJson =
    """{
      |  "metaData" : {
      |    "id" : "testa",
      |    "typeSpecificData" : {
      |      "parallelism" : 10,
      |      "spillStateToDisk" : true,
      |      "useAsyncInterpretation" : null,
      |      "checkpointIntervalInSeconds" : 11,
      |      "type" : "StreamMetaData"
      |    },
      |    "additionalFields" : null
      |  },
      |  "nodes" : [
      |  ],
      |  "additionalBranches" : [
      |  ]
      |}""".stripMargin

  val newJson =
    """{
      |  "metaData" : {
      |    "id" : "testa",
      |    "additionalFields" : {
      |       "description": null,
      |       "properties" : {
      |         "parallelism" : "10",
      |         "spillStateToDisk" : "true",
      |         "useAsyncInterpretation" : "",
      |         "checkpointIntervalInSeconds" : "11"
      |       },
      |       "metaDataType": "StreamMetaData"
      |    }
      |  },
      |  "nodes" : [
      |  ],
      |  "additionalBranches" : [
      |  ]
      |}""".stripMargin


  it should "decode old json" in {
    ProcessMarshaller.fromJson(oldJson).isValid shouldBe true
  }

  it should "decode new json" in {
      ProcessMarshaller.fromJson(newJson).isValid shouldBe true
  }

  it should "decode new json and old to the same structure" in {
    val oldCP = ProcessMarshaller.fromJson(oldJson).getOrElse(throw new AssertionError())
    val newCP = ProcessMarshaller.fromJson(newJson).getOrElse(throw new AssertionError())
    oldCP shouldBe newCP
  }

}
