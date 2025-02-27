package db.migration

import io.circe.Json
import io.circe.syntax._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, RequestResponseMetaData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

class V1_032__StandaloneToRequestResponseSpec extends AnyFlatSpec with Matchers {

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

  private lazy val legacyStandaloneScenario = wrapEmptyScenario {
    s"""{
      |  "path": "/main",
      |  "type": "StandaloneMetaData"
      |}
      |""".stripMargin
  }

  private lazy val streamScenario = wrapEmptyScenario {
    s"""{
      |  "parallelism": 2,
      |  "spillStateToDisk": true,
      |  "useAsyncInterpretation": null,
      |  "checkpointIntervalInSeconds": null,
      |  "type": "StreamMetaData"
      |}
      |""".stripMargin
  }

  private lazy val requestResponseScenario = wrapEmptyScenario {
    s"""{
      |  "path": "/main",
      |  "type": "RequestResponseMetaData"
      |}
      |""".stripMargin
  }

  private lazy val newRequestResponseScenario = wrapEmptyScenario {
    s"""{
       |  "slug": "/main",
       |  "type": "RequestResponseMetaData"
       |}
       |""".stripMargin
  }

  private def parse(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Failed to decode")

  it should "convert standalone type" in {
    // we need to apply both migrations because both has influence on shape of expected metadata
    val migrateMetadata = V1_032__StandaloneToRequestResponseDefinition.migrateMetadata _ andThen { o =>
      o.flatMap(V1_033__RequestResponseUrlToSlug.migrateMetadata)
    }
    migrateMetadata(legacyStandaloneScenario).get shouldBe newRequestResponseScenario
    migrateMetadata(requestResponseScenario).get shouldBe newRequestResponseScenario
    migrateMetadata(streamScenario).get shouldBe streamScenario
  }

}
