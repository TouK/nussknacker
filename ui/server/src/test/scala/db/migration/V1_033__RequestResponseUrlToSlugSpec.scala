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

  private def wrapEmptyProcess(typeSpecificData: String): Json = parse(
    s"""{
      |  "metaData": {
      |    "id": "$id",
      |    "typeSpecificData": $typeSpecificData,
      |    "additionalFields" : null,
      |    "subprocessVersions": {}
      |  },
      |  "nodes": [],
      |  "additionalBranches": []
      |}
      |""".stripMargin
  )

  private lazy val legacyRequestResponseMetaData = wrapEmptyProcess {
    s"""{
       |  "path": "main",
       |  "type": "RequestResponseMetaData"
       |}
       |""".stripMargin
  }

  private lazy val requestResponseMetaData = wrapEmptyProcess {
    s"""{
       |  "slug": "main",
       |  "type": "RequestResponseMetaData"
       |}
       |""".stripMargin
  }

  private lazy val streamMetaData = wrapEmptyProcess {
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

  private def toJson(metaData: MetaData) = Some(CanonicalProcess(metaData, Nil).asJson)

  test("convert request response url to slug") {
    migrateMetadata(legacyRequestResponseMetaData) shouldBe toJson(MetaData(id, RequestResponseMetaData(slug = Some("main"))))
    migrateMetadata(requestResponseMetaData) shouldBe toJson(MetaData(id, RequestResponseMetaData(slug = Some("main"))))
    migrateMetadata(streamMetaData) shouldBe toJson(MetaData(id, StreamMetaData(parallelism = Some(2))))
  }
}
