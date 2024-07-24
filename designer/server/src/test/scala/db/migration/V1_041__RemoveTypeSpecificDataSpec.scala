package db.migration

import db.migration.V1_041__RemoveTypeSpecificDataDefinition.migrateMetaData
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil
import io.circe.Json

class V1_041__RemoveTypeSpecificDataSpec extends AnyFunSuite with Matchers {

  private def parse(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Failed to decode")

  private def wrapEmptyScenario(metaData: String): Json = parse(
    s"""{
       |  "metaData": $metaData,
       |  "nodes": [],
       |  "additionalBranches": []
       |}
       |""".stripMargin
  )

  private val legacyFlinkScenarioNoFields = wrapEmptyScenario {
    """{
       |  "id": "testId",
       |  "typeSpecificData": {
       |    "parallelism": 10,
       |    "spillStateToDisk": true,
       |    "useAsyncInterpretation": null,
       |    "checkpointIntervalInSeconds": 1000,
       |    "type": "StreamMetaData"
       |  }
       |}
       |""".stripMargin
  }

  private val updatedFlinkScenarioNoFields = wrapEmptyScenario {
    """{
       |  "id": "testId",
       |  "additionalFields": {
       |    "description": null,
       |    "properties": {
       |      "parallelism" : "10",
       |      "spillStateToDisk" : "true",
       |      "useAsyncInterpretation" : "",
       |      "checkpointIntervalInSeconds" : "1000"
       |    },
       |    "metaDataType": "StreamMetaData"
       |  }
       |}
       |""".stripMargin
  }

  private val legacyFlinkScenarioWithDescriptionNoProperties = wrapEmptyScenario {
    """{
      |  "id": "testId",
      |  "typeSpecificData": {
      |    "parallelism": 10,
      |    "spillStateToDisk": true,
      |    "useAsyncInterpretation": null,
      |    "checkpointIntervalInSeconds": 1000,
      |    "type": "StreamMetaData"
      |  },
      |  "additionalFields" : {
      |    "description": "someDescription"
      |  }
      |}
      |""".stripMargin
  }

  private val updatedFlinkScenarioWithDescriptionNoProperties = wrapEmptyScenario {
    """{
      |  "id": "testId",
      |  "additionalFields": {
      |    "description": "someDescription",
      |    "properties": {
      |      "parallelism" : "10",
      |      "spillStateToDisk" : "true",
      |      "useAsyncInterpretation" : "",
      |      "checkpointIntervalInSeconds" : "1000"
      |    },
      |    "metaDataType": "StreamMetaData"
      |  }
      |}
      |""".stripMargin
  }

  private val legacyFlinkScenarioWithScenarioProperties = wrapEmptyScenario {
    """{
      |  "id": "testId",
      |  "typeSpecificData": {
      |    "parallelism": 10,
      |    "spillStateToDisk": true,
      |    "useAsyncInterpretation": null,
      |    "checkpointIntervalInSeconds": 1000,
      |    "type": "StreamMetaData"
      |  },
      |  "additionalFields" : {
      |    "description": "someDescription",
      |    "properties": {
      |      "someProperty1": "",
      |      "someProperty2": "someValue2"
      |    }
      |  }
      |}
      |""".stripMargin
  }

  private val updatedFlinkScenarioWithScenarioProperties = wrapEmptyScenario {
    """{
      |  "id": "testId",
      |  "additionalFields": {
      |    "description": "someDescription",
      |    "properties": {
      |      "parallelism" : "10",
      |      "spillStateToDisk" : "true",
      |      "useAsyncInterpretation" : "",
      |      "checkpointIntervalInSeconds" : "1000",
      |      "someProperty1": "",
      |      "someProperty2": "someValue2"
      |    },
      |    "metaDataType": "StreamMetaData"
      |  }
      |}
      |""".stripMargin
  }

  private val legacyLiteStreamScenario = wrapEmptyScenario {
    """{
      |  "id": "testId",
      |  "typeSpecificData": {
      |    "parallelism" : "10",
      |    "type": "LiteStreamMetaData"
      |  }
      |}
      |""".stripMargin
  }

  private val updatedLiteStreamScenario = wrapEmptyScenario {
    """{
      |  "id": "testId",
      |  "additionalFields": {
      |    "description": null,
      |    "properties": {
      |      "parallelism" : "10"
      |    },
      |    "metaDataType": "LiteStreamMetaData"
      |  }
      |}
      |""".stripMargin
  }

  private val legacyLiteRequestResponseScenario = wrapEmptyScenario {
    """{
      |  "id": "testId",
      |  "typeSpecificData": {
      |    "slug" : "someSlug",
      |    "type": "RequestResponseMetaData"
      |  }
      |}
      |""".stripMargin
  }

  private val updatedLiteRequestResponseScenario = wrapEmptyScenario {
    """{
      |  "id": "testId",
      |  "additionalFields": {
      |    "description": null,
      |    "properties": {
      |      "slug" : "someSlug"
      |    },
      |    "metaDataType": "RequestResponseMetaData"
      |  }
      |}
      |""".stripMargin
  }

  private val legacyFragment = wrapEmptyScenario {
    """{
      |  "id": "testId",
      |  "typeSpecificData": {
      |    "docsUrl" : "someUrl",
      |    "type": "FragmentSpecificData"
      |  }
      |}
      |""".stripMargin
  }

  private val updatedFragment = wrapEmptyScenario {
    """{
      |  "id": "testId",
      |  "additionalFields": {
      |    "description": null,
      |    "properties": {
      |      "docsUrl" : "someUrl",
      |      "componentGroup" : "",
      |      "icon": ""
      |    },
      |    "metaDataType": "FragmentSpecificData"
      |  }
      |}
      |""".stripMargin
  }

  test("migrate flink scenario") {
    migrateMetaData(legacyFlinkScenarioNoFields) shouldBe Right(updatedFlinkScenarioNoFields)
    migrateMetaData(legacyFlinkScenarioWithDescriptionNoProperties) shouldBe Right(
      updatedFlinkScenarioWithDescriptionNoProperties
    )
    migrateMetaData(legacyFlinkScenarioWithScenarioProperties) shouldBe Right(
      updatedFlinkScenarioWithScenarioProperties
    )
  }

  test("migrate lite stream scenario") {
    migrateMetaData(legacyLiteStreamScenario) shouldBe Right(updatedLiteStreamScenario)
  }

  test("migrate lite request response scenario") {
    migrateMetaData(legacyLiteRequestResponseScenario) shouldBe Right(updatedLiteRequestResponseScenario)
  }

  test("migrate fragment") {
    migrateMetaData(legacyFragment) shouldBe Right(updatedFragment)
  }

}
