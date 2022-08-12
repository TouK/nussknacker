package db.migration

import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

class V1_016__TypeSpecificMetaDataChangeSpec extends AnyFlatSpec with Matchers {

  it should "convert json" in {

    val oldJson =
      CirceUtil.decodeJsonUnsafe[Json](
        """{"metaData":{"id":"DEFGH","parallelism":3,
          |"additionalFields":{"groups":[]}},
          |"nodes":[]}""".stripMargin, "invalid scenario")

    val converted = V1_016__TypeSpecificMetaDataChange.updateMetaData(oldJson).flatMap(ProcessMarshaller.fromJson(_).toOption)

    val metaData = converted.map(_.metaData)
    

    metaData shouldBe Some(MetaData("DEFGH", StreamMetaData(parallelism = Some(3)), Some(ProcessAdditionalFields(None, Map.empty))))
  }
}
