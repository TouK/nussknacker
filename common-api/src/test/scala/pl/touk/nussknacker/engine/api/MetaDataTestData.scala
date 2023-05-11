package pl.touk.nussknacker.engine.api

import org.scalatest.prop.TableFor3
import org.scalatest.prop.TableDrivenPropertyChecks.Table

object MetaDataTestData {

  // flink
  val flinkEmptyTypeData: StreamMetaData = StreamMetaData(None, None, None, None)
  val flinkFullTypeData: StreamMetaData = StreamMetaData(
    parallelism = Some(5),
    spillStateToDisk = Some(false),
    useAsyncInterpretation = Some(true),
    checkpointIntervalInSeconds = Some(1000L)
  )
  val flinkFullProperties: Map[String, String] = Map(
    "parallelism" -> "5",
    "spillStateToDisk" -> "false",
    "useAsyncInterpretation" -> "true",
    "checkpointIntervalInSeconds" -> "1000"
  )
  val flinkInvalidProperties: Map[String, String] = Map(
    "parallelism" -> "non-int",
    "spillStateToDisk" -> "non-boolean",
    "useAsyncInterpretation" -> "non-boolean",
    "checkpointIntervalInSeconds" -> "non-long"
  )

  // lite stream
  val liteStreamEmptyTypeData: LiteStreamMetaData = LiteStreamMetaData(None)
  val liteStreamFullTypeData: LiteStreamMetaData = LiteStreamMetaData(parallelism = Some(5))
  val liteStreamFullProperties: Map[String, String] = Map("parallelism" -> "5")
  val liteStreamInvalidProperties: Map[String, String] = Map("parallelism" -> "non-int")

  // request-response
  val requestResponseFullTypeData: RequestResponseMetaData = RequestResponseMetaData(slug = Some("exampleSlug"))
  val requestResponseFullProperties: Map[String, String] = Map("slug" -> "exampleSlug")

  // fragment
  val fragmentFullTypeData: FragmentSpecificData = FragmentSpecificData(docsUrl = Some("exampleUrl"))
  val fragmentFullProperties: Map[String, String] = Map("docsUrl" -> "exampleUrl")


  val flinkMetaDataName = "StreamMetaData"
  val liteStreamMetaDataName = "LiteStreamMetaData"
  val requestResponseMetaDataName = "RequestResponseMetaData"
  val fragmentMetaDataName = "FragmentSpecificData"

  val genericProperties: Map[String, String] = Map(
    "aProperty" -> "aValue",
    "emptyProperty" -> ""
  )

  val fullMetaDataCases: TableFor3[Map[String, String], String, TypeSpecificData] = Table(
    ("properties", "metaDataName", "typeSpecificData"),
    (flinkFullProperties, flinkMetaDataName, flinkFullTypeData),
    (liteStreamFullProperties, liteStreamMetaDataName, liteStreamFullTypeData),
    (requestResponseFullProperties, requestResponseMetaDataName, requestResponseFullTypeData),
    (fragmentFullProperties, fragmentMetaDataName, fragmentFullTypeData)
  )

  val invalidMetaDataCases: TableFor3[Map[String, String], String, TypeSpecificData] = Table(
    ("properties", "metaDataName", "typeSpecificData"),
    (flinkInvalidProperties, flinkMetaDataName, flinkEmptyTypeData),
    (liteStreamInvalidProperties, liteStreamMetaDataName, liteStreamEmptyTypeData)
  )

}
