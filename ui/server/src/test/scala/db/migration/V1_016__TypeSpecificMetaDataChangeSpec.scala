package db.migration

import io.circe.Json
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{CirceUtil, MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.collection.immutable.ListMap

class V1_016__TypeSpecificMetaDataChangeSpec extends FlatSpec with Matchers {

  it should "convert json" in {

    val oldJson =
      CirceUtil.decodeJsonUnsafe[Json](
        """{"metaData":{"id":"DEFGH","parallelism":3,
          |"additionalFields":{"groups":[]}},
          |"exceptionHandlerRef": {"parameters":[]},"nodes":[]}""".stripMargin, "invalid process")

    val converted = V1_016__TypeSpecificMetaDataChange.updateMetaData(oldJson).flatMap(js => ProcessMarshaller.fromJson(js.noSpaces).toOption)

    val metaData = converted.map(_.metaData)
    

    metaData shouldBe Some(MetaData("DEFGH", StreamMetaData(parallelism = Some(3)), false, Some(ProcessAdditionalFields(None, Set(), ListMap.empty))))
  }
}