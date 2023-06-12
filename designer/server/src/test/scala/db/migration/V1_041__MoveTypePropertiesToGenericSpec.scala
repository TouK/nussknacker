package db.migration

import db.migration.V1_041__MoveTypePropertiesToGenericDefinition.migrateMetaData
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import io.circe.Json
import io.circe.syntax._

class V1_041__MoveTypePropertiesToGenericSpec extends AnyFunSuite with Matchers {


  private val legacyFlinkScenario: Json = parse(
      """{
         |  "metaData": {
         |    "id": "someId",
         |    "typeSpecificData": {
         |        "parallelism": 10,
         |        "spillStateToDisk": true,
         |        "useAsyncInterpretation": null,
         |        "checkpointIntervalInSeconds": 1000,
         |        "type": "StreamMetaData"
         |    },
         |    "additionalFields": null
         |  },
         |  "nodes": [],
         |  "additionalBranches": []
         |}
         |""".stripMargin)

  private val updatedFlinkScenario: Json = parse(
    s"""{
       |  "metaData": {
       |    "id": "someId",
       |    "additionalFields": {
       |      "description": null,
       |       "properties": {
       |         "parallelism" : "10",
       |         "spillStateToDisk" : "true",
       |         "useAsyncInterpretation" : "",
       |         "checkpointIntervalInSeconds" : "1000"
       |      },
       |      "metaDataType": "StreamMetaData"
       |    }
       |  },
       |  "nodes": [],
       |  "additionalBranches": []
       |}
       |""".stripMargin)


  private def parse(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Failed to decode")

  test("test") {
    migrateMetaData(legacyFlinkScenario) shouldBe Some(updatedFlinkScenario)

    val updated = updatedFlinkScenario.as[CanonicalProcess]
  }


}
