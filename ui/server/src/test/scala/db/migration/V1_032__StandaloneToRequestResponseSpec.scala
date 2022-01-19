package db.migration

import db.migration.V1_032__StandaloneToRequestResponseDefinition.migrateMetadata
import io.circe.Json
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, RequestResponseMetaData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

class V1_032__StandaloneToRequestResponseSpec extends FlatSpec with Matchers {

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

  private lazy val legacyStandaloneMetaData = wrapEmptyProcess {
    s"""{
      |  "path": "/main",
      |   "type": "StandaloneMetaData"
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

  private lazy val requestResponseMetaData = wrapEmptyProcess {
    s"""{
      |  "path": "/main",
      |  "type": "RequestResponseMetaData"
      |}
      |""".stripMargin
  }

  private def parse(str: String): Json = CirceUtil.decodeJsonUnsafe[Json](str, "Failed to decode")

  private def toJson(metaData: MetaData) = Some(ProcessMarshaller.toGraphProcess(CanonicalProcess(metaData, Nil)).json)

  it should "convert standalone type" in {
    migrateMetadata(legacyStandaloneMetaData) shouldBe toJson(MetaData(id, RequestResponseMetaData(Some("/main"))))
    migrateMetadata(requestResponseMetaData) shouldBe toJson(MetaData(id, RequestResponseMetaData(Some("/main"))))
    migrateMetadata(streamMetaData) shouldBe toJson(MetaData(id, StreamMetaData(parallelism = Some(2))))
  }
}
