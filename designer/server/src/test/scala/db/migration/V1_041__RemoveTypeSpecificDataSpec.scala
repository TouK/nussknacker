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

  private val legacyFlinkMetaDataNoFields = parse {
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

  private val updatedFlinkMetaDataNoFields = parse {
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

  private val legacyFlinkMetaDataWithDescriptionNoProperties = parse {
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

  private val updatedFlinkMetaDataWithDescriptionNoProperties = parse {
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

  private val legacyFlinkMetaDataWithScenarioProperties = parse {
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

  private val updatedFlinkMetaDataWithScenarioProperties = parse {
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

  private val legacyLiteStreamMetaData = parse {
    """{
      |  "id": "testId",
      |  "typeSpecificData": {
      |    "parallelism" : "10",
      |    "type": "LiteStreamMetaData"
      |  }
      |}
      |""".stripMargin
  }

  private val updatedLiteStreamMetaData = parse {
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

  private val legacyLiteRequestResponseMetaData = parse {
    """{
      |  "id": "testId",
      |  "typeSpecificData": {
      |    "slug" : "someSlug",
      |    "type": "RequestResponseMetaData"
      |  }
      |}
      |""".stripMargin
  }

  private val updatedLiteRequestResponseMetaData = parse {
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

  private val legacyFragmentMetaData = parse {
    """{
      |  "id": "testId",
      |  "typeSpecificData": {
      |    "docsUrl" : "someUrl",
      |    "type": "FragmentSpecificData"
      |  }
      |}
      |""".stripMargin
  }

  private val updatedFragmentMetaData = parse {
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
    migrateAndGetMetaData(legacyFlinkMetaDataNoFields) shouldBe updatedFlinkMetaDataNoFields
    migrateAndGetMetaData(
      legacyFlinkMetaDataWithDescriptionNoProperties
    ) shouldBe updatedFlinkMetaDataWithDescriptionNoProperties
    migrateAndGetMetaData(legacyFlinkMetaDataWithScenarioProperties) shouldBe updatedFlinkMetaDataWithScenarioProperties
  }

  test("migrate lite stream scenario") {
    migrateAndGetMetaData(legacyLiteStreamMetaData) shouldBe updatedLiteStreamMetaData
  }

  test("migrate lite request response scenario") {
    migrateAndGetMetaData(legacyLiteRequestResponseMetaData) shouldBe updatedLiteRequestResponseMetaData
  }

  test("migrate fragment") {
    migrateAndGetMetaData(legacyFragmentMetaData) shouldBe updatedFragmentMetaData
  }

  private def migrateAndGetMetaData(metadataJson: Json): Json = {
    migrateMetaData(wrapEmptyScenario(metadataJson.noSpaces)).rightValue.asObject
      .flatMap(_.apply("metaData"))
      .value
  }

}
