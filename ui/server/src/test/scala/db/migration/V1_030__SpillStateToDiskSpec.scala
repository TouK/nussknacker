package db.migration

import io.circe.Json
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil

class V1_030__SpillStateToDiskSpec extends FlatSpec with Matchers {

  private lazy val expectedJsonWithSpillStateToDisk = {
    val rawJsonString =
      """{
        |  "metaData": {
        |    "id": "empty-2",
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
    Some(CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string."))
  }

  private lazy val expectedJsonWithoutSpillStateToDisk = {
    val rawJsonString =
      """{
        |  "metaData": {
        |    "id": "empty-2",
        |    "typeSpecificData": {
        |      "parallelism": 1,
        |      "useAsyncInterpretation": null,
        |      "checkpointIntervalInSeconds": null,
        |      "type": "StreamMetaData"
        |    }
        |  }
        |}
        |""".stripMargin
    Some(CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string."))
  }

  it should "convert exists splitStateToDisk to spillStateToDisk" in {
    val rawJsonString =
      """
        |{
        |  "metaData": {
        |    "id": "empty-2",
        |    "typeSpecificData": {
        |      "parallelism": 1,
        |      "splitStateToDisk": true,
        |      "useAsyncInterpretation": null,
        |      "checkpointIntervalInSeconds": null,
        |      "type": "StreamMetaData"
        |    }
        |  }
        |}
        |""".stripMargin

    val oldJson = CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string.")
    val converted = V1_030__SpillStateToDisk.renameSpillStateToDisk(oldJson)

    converted shouldBe expectedJsonWithSpillStateToDisk
  }

  it should "do noting when property not specified" in {
    val rawJsonString =
      """
        |{
        |  "metaData": {
        |    "id": "empty-2",
        |    "typeSpecificData": {
        |      "parallelism": 1,
        |      "useAsyncInterpretation": null,
        |      "checkpointIntervalInSeconds": null,
        |      "type": "StreamMetaData"
        |    }
        |  }
        |}
        |""".stripMargin

    val oldJson = CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string.")
    val converted = V1_030__SpillStateToDisk.renameSpillStateToDisk(oldJson)

    converted shouldBe expectedJsonWithoutSpillStateToDisk
  }
}
