package db.migration

import db.migration.V1_041__RemoveTypeSpecificDataDefinition.migrateMetaData
import io.circe.Json
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.test.EitherValuesDetailedMessage

class V1_041__RemoveTypeSpecificDataSpec
    extends AnyFunSuite
    with Matchers
    with EitherValuesDetailedMessage
    with OptionValues {

  private def parse(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Failed to decode")

  private def wrapEmptyScenario(metaData: String): Json = parse(
    s"""{
       |  "metaData": $metaData,
       |  "nodes": [],
       |  "additionalBranches": []
       |}
       |""".stripMargin
  )

  private val legacyFlinkScenarioNoFields = parse {
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

  private val updatedFlinkScenarioNoFields = parse {
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
       |    "metaDataType": "StreamMetaData",
       |    "showDescription": false
       |  }
       |}
       |""".stripMargin
  }

  private val legacyFlinkScenarioWithDescriptionNoProperties = parse {
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

  private val updatedFlinkScenarioWithDescriptionNoProperties = parse {
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
      |    "metaDataType": "StreamMetaData",
      |    "showDescription": false
      |  }
      |}
      |""".stripMargin
  }

  private val legacyFlinkScenarioWithScenarioProperties = parse {
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

  private val updatedFlinkScenarioWithScenarioProperties = parse {
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
      |    "metaDataType": "StreamMetaData",
      |    "showDescription": false
      |  }
      |}
      |""".stripMargin
  }

  private val legacyLiteStreamScenario = parse {
    """{
      |  "id": "testId",
      |  "typeSpecificData": {
      |    "parallelism" : "10",
      |    "type": "LiteStreamMetaData"
      |  }
      |}
      |""".stripMargin
  }

  private val updatedLiteStreamScenario = parse {
    """{
      |  "id": "testId",
      |  "additionalFields": {
      |    "description": null,
      |    "properties": {
      |      "parallelism" : "10"
      |    },
      |    "metaDataType": "LiteStreamMetaData",
      |    "showDescription": false
      |  }
      |}
      |""".stripMargin
  }

  private val legacyLiteRequestResponseScenario = parse {
    """{
      |  "id": "testId",
      |  "typeSpecificData": {
      |    "slug" : "someSlug",
      |    "type": "RequestResponseMetaData"
      |  }
      |}
      |""".stripMargin
  }

  private val updatedLiteRequestResponseScenario = parse {
    """{
      |  "id": "testId",
      |  "additionalFields": {
      |    "description": null,
      |    "properties": {
      |      "slug" : "someSlug"
      |    },
      |    "metaDataType": "RequestResponseMetaData",
      |    "showDescription": false
      |  }
      |}
      |""".stripMargin
  }

  private val legacyFragment = parse {
    """{
      |  "id": "testId",
      |  "typeSpecificData": {
      |    "docsUrl" : "someUrl",
      |    "type": "FragmentSpecificData"
      |  }
      |}
      |""".stripMargin
  }

  private val updatedFragment = parse {
    """{
      |  "id": "testId",
      |  "additionalFields": {
      |    "description": null,
      |    "properties": {
      |      "docsUrl" : "someUrl",
      |      "componentGroup" : "",
      |      "icon": ""
      |    },
      |    "metaDataType": "FragmentSpecificData",
      |    "showDescription": false
      |  }
      |}
      |""".stripMargin
  }

  test("migrate flink scenario") {
    migrateAndGetMetaData(legacyFlinkScenarioNoFields) shouldBe updatedFlinkScenarioNoFields
    migrateAndGetMetaData(
      legacyFlinkScenarioWithDescriptionNoProperties
    ) shouldBe updatedFlinkScenarioWithDescriptionNoProperties
    migrateAndGetMetaData(legacyFlinkScenarioWithScenarioProperties) shouldBe updatedFlinkScenarioWithScenarioProperties
  }

  test("migrate lite stream scenario") {
    migrateAndGetMetaData(legacyLiteStreamScenario) shouldBe updatedLiteStreamScenario
  }

  test("migrate lite request response scenario") {
    migrateAndGetMetaData(legacyLiteRequestResponseScenario) shouldBe updatedLiteRequestResponseScenario
  }

  test("migrate fragment") {
    migrateAndGetMetaData(legacyFragment) shouldBe updatedFragment
  }

  private def migrateAndGetMetaData(metadataJson: Json): Json = {
    migrateMetaData(wrapEmptyScenario(metadataJson.noSpaces)).rightValue.asObject
      .flatMap(_.apply("metaData"))
      .value
  }

}
