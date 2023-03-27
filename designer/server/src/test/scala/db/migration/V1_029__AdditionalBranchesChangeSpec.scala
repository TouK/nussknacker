package db.migration

import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil

class V1_029__AdditionalBranchesChangeSpec extends AnyFlatSpec with Matchers {

  private lazy val expectedJson = {
    val rawJsonString = """{"metaData":{"id":"empty-2","isSubprocess":false},"additionalBranches":[]}"""
    Some(CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string."))
  }

  it should "convert exists additionalBranches null json to empty array json" in {
    val rawJsonString =
      """
        |{
        |  "metaData": {
        |    "id": "empty-2",
        |    "isSubprocess": false
        |  },
        |  "additionalBranches": null
        |}
        |""".stripMargin

    val oldJson = CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string.")
    val converted = V1_029__AdditionalBranchesChange.updateAdditionalBranches(oldJson)

    converted shouldBe expectedJson
  }

  it should "convert not exists additionalBranches json to empty array json" in {
    val rawJsonString =
      """
        |{
        |  "metaData": {
        |    "id": "empty-2",
        |    "isSubprocess": false
        |  }
        |}
        |""".stripMargin

    val oldJson = CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string.")
    val converted = V1_029__AdditionalBranchesChange.updateAdditionalBranches(oldJson)

    converted shouldBe expectedJson
  }

  it should "not create any changes in proper additionalBranches json data" in {
    val rawJsonString =
      """
        |{
        |  "metaData": {
        |    "id": "empty-2",
        |    "isSubprocess": false
        |  },
        |  "additionalBranches": ["test"]
        |}
        |""".stripMargin

    val oldJson = CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string.")
    val converted = V1_029__AdditionalBranchesChange.updateAdditionalBranches(oldJson)

    converted shouldBe Some(oldJson)
  }
}