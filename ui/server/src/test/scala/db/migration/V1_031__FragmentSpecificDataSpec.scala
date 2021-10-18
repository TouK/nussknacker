package db.migration

import io.circe.Json
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil

class V1_031__FragmentSpecificDataSpec extends FlatSpec with Matchers {

  private lazy val expectedScenario = {
    val rawJsonString =
      """{
        |  "metaData": {
        |    "id": "empty-2",
        |    "typeSpecificData": {
        |      "parallelism": 2,
        |      "spillStateToDisk": true,
        |      "useAsyncInterpretation": null,
        |      "checkpointIntervalInSeconds": null,
        |      "type": "StreamMetaData"
        |    }
        |  }
        |}
        |""".stripMargin
    Some(CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string."))
  }

  private lazy val expectedFragment = {
    val rawJsonString =
      """{
        |  "metaData": {
        |    "id": "empty-2",
        |    "typeSpecificData": {
        |      "docsUrl": null,
        |      "type": "FragmentSpecificData"
        |    }
        |  }
        |}
        |""".stripMargin
    Some(CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string."))
  }

  it should "convert scenario metadata" in {
    val rawJsonString =
      """
        |{
        |  "metaData": {
        |    "id": "empty-2",
        |    "isSubprocess": false,
        |    "typeSpecificData": {
        |      "parallelism": 2,
        |      "spillStateToDisk": true,
        |      "useAsyncInterpretation": null,
        |      "checkpointIntervalInSeconds": null,
        |      "type": "StreamMetaData"
        |    }
        |  }
        |}
        |""".stripMargin

    val oldJson = CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string.")
    val converted = V1_031__FragmentSpecificData.migrateMetadata(oldJson)

    converted shouldBe expectedScenario
  }


  it should "convert fragment metadata" in {
    val rawJsonString =
      """
        |{
        |  "metaData": {
        |    "id": "empty-2",
        |    "isSubprocess": true,
        |    "typeSpecificData": {
        |      "parallelism": 1,
        |      "spillStateToDisk": true,
        |      "useAsyncInterpretation": null,
        |      "checkpointIntervalInSeconds": null,
        |      "type": "StreamMetaData"
        |    }
        |  }
        |}
        |""".stripMargin

    val oldJson = CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string.")
    val converted = V1_031__FragmentSpecificData.migrateMetadata(oldJson)

    converted shouldBe expectedFragment
  }


}
