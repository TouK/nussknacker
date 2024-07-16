package db.migration

import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil

class V1_054__FragmentIsDeprecatedPropertyMigrationSpec extends AnyFlatSpec with Matchers {

  it should "set isDeprecated to false if not present" in {
    val rawJsonString =
      """
        |{
        |  "metaData": {
        |    "id": "empty-2",
        |    "additionalFields": {
        |       "properties": {}
        |     },
        |    "typeSpecificData": {
        |      "docsUrl": null,
        |      "type": "FragmentSpecificData"
        |    }
        |  }
        |}
        |""".stripMargin

    val oldJson = CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string.")

    val migratedJson = V1_054__FragmentAddIsDeprecatedProperty.migrateMetadata(oldJson)

    migratedJson shouldBe defined
    migratedJson.get.hcursor
      .downField("metaData")
      .downField("additionalFields")
      .downField("properties")
      .get[Boolean]("isDeprecated") shouldBe Right(false)
  }

  it should "leave isDeprecated unchanged if present" in {
    val rawJsonString =
      """
        |{
        |  "metaData": {
        |    "id": "empty-2",
        |    "additionalFields": {
        |       "properties": {
        |         "isDeprecated": true
        |       }
        |     },
        |    "typeSpecificData": {
        |      "docsUrl": null,
        |      "type": "FragmentSpecificData"
        |    }
        |  }
        |}
        |""".stripMargin

    val oldJson = CirceUtil.decodeJsonUnsafe[Json](rawJsonString, "Invalid json string.")

    val migratedJson = V1_054__FragmentAddIsDeprecatedProperty.migrateMetadata(oldJson)

    migratedJson shouldBe defined
    migratedJson.get.hcursor
      .downField("metaData")
      .downField("additionalFields")
      .downField("properties")
      .get[Boolean]("isDeprecated") shouldBe Right(true)
  }

}
