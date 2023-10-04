package db.migration

import db.migration.V1_033__RequestResponseUrlToSlug.migrateMetadata
import io.circe.Json
import io.circe.syntax._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, RequestResponseMetaData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

class V1_033__RequestResponseUrlToSlugSpec extends AnyFunSuite with Matchers {

  private val id = "id1"

  private def wrapEmptyScenario(typeSpecificData: String): Json = parse(
    s"""{
      |  "metaData": {
      |    "id": "$id",
      |    "typeSpecificData": $typeSpecificData,
      |    "additionalFields" : null
      |  },
      |  "nodes": [],
      |  "additionalBranches": []
      |}
      |""".stripMargin
  )

  private lazy val legacyRequestResponseScenario = wrapEmptyScenario {
    s"""{
       |  "path": "main",
       |  "type": "RequestResponseMetaData"
       |}
       |""".stripMargin
  }

  private lazy val newRequestResponseScenario = wrapEmptyScenario {
    s"""{
       |  "slug": "main",
       |  "type": "RequestResponseMetaData"
       |}
       |""".stripMargin
  }

  private lazy val flinkScenario = wrapEmptyScenario {
    s"""{
       |  "parallelism": 2,
       |  "spillStateToDisk": true,
       |  "useAsyncInterpretation": null,
       |  "checkpointIntervalInSeconds": null,
       |  "type": "StreamMetaData"
       |}
       |""".stripMargin
  }

  private def parse(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Failed to decode")

  test("convert request response url to slug") {
    migrateMetadata(legacyRequestResponseScenario).get shouldBe newRequestResponseScenario
    migrateMetadata(flinkScenario).get shouldBe flinkScenario
  }

}
