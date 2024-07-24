package db.migration

import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil

class V1_054__FragmentAddComponentGroupPropertyMigrationSpec extends AnyFlatSpec with Matchers {

  it should "set componentGroup to 'fragments' if not present" in {
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

    val migratedJson = V1_054__FragmentAddComponentGroupProperty.migrateMetadata(oldJson)

    migratedJson shouldBe defined
    migratedJson.get.hcursor
      .downField("metaData")
      .downField("additionalFields")
      .downField("properties")
      .get[String]("componentGroup") shouldBe Right("fragments")
  }

  it should "leave componentGroup unchanged if present" in {
    val rawJsonString =
      """
        |{
        |  "metaData": {
        |    "id": "empty-2",
        |    "additionalFields": {
        |       "properties": {
        |         "componentGroup": "custom"
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

    val migratedJson = V1_054__FragmentAddComponentGroupProperty.migrateMetadata(oldJson)

    migratedJson shouldBe defined
    migratedJson.get.hcursor
      .downField("metaData")
      .downField("additionalFields")
      .downField("properties")
      .get[String]("componentGroup") shouldBe Right("custom")
  }

}
