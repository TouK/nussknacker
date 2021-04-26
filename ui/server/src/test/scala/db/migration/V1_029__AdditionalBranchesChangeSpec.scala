package db.migration

import io.circe.Json
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.CirceUtil

class V1_029__AdditionalBranchesChangeSpec extends FlatSpec with Matchers {

  private lazy val exceptedJson = {
    val rawJsonString = """{"metaData":{"id":"empty-2","isSubprocess":false,"subprocessVersions":{}},"additionalBranches":[]}"""
    Some(CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string."))
  }

  it should "convert exists additionalBranches null json to empty array json" in {
    val rawJsonString =
      """
        |{
        |  "metaData": {
        |    "id": "empty-2",
        |    "isSubprocess": false,
        |    "subprocessVersions": {}
        |  },
        |  "additionalBranches": null
        |}
        |""".stripMargin

    val oldJson = CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string.")
    val converted = V1_029__AdditionalBranchesChange.updateAdditionalBranches(oldJson)

    converted shouldBe exceptedJson
  }

  it should "convert not exists additionalBranches json to empty array json" in {
    val rawJsonString =
      """
        |{
        |  "metaData": {
        |    "id": "empty-2",
        |    "isSubprocess": false,
        |    "subprocessVersions": {}
        |  }
        |}
        |""".stripMargin

    val oldJson = CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string.")
    val converted = V1_029__AdditionalBranchesChange.updateAdditionalBranches(oldJson)

    converted shouldBe exceptedJson
  }
}