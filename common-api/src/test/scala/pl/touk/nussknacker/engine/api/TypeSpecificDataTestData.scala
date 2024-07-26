package pl.touk.nussknacker.engine.api

import org.scalatest.prop.TableFor3
import org.scalatest.prop.TableDrivenPropertyChecks.Table

object TypeSpecificDataTestData {

  // flink
  val flinkEmptyTypeData: StreamMetaData = StreamMetaData(None, None, None, None)

  val flinkFullTypeData: StreamMetaData = StreamMetaData(
    parallelism = Some(5),
    spillStateToDisk = Some(false),
    useAsyncInterpretation = Some(true),
    checkpointIntervalInSeconds = Some(1000L)
  )

  val flinkEmptyProperties: Map[String, String] = Map(
    "parallelism"                 -> "",
    "spillStateToDisk"            -> "",
    "useAsyncInterpretation"      -> "",
    "checkpointIntervalInSeconds" -> ""
  )

  val flinkFullProperties: Map[String, String] = Map(
    "parallelism"                 -> "5",
    "spillStateToDisk"            -> "false",
    "useAsyncInterpretation"      -> "true",
    "checkpointIntervalInSeconds" -> "1000"
  )

  val flinkInvalidTypeProperties: Map[String, String] = Map(
    "parallelism"                 -> "non-int",
    "spillStateToDisk"            -> "non-boolean",
    "useAsyncInterpretation"      -> "non-boolean",
    "checkpointIntervalInSeconds" -> "non-long"
  )

  // lite stream
  val liteStreamEmptyTypeData: LiteStreamMetaData          = LiteStreamMetaData(None)
  val liteStreamFullTypeData: LiteStreamMetaData           = LiteStreamMetaData(parallelism = Some(5))
  val liteStreamEmptyProperties: Map[String, String]       = Map("parallelism" -> "")
  val liteStreamFullProperties: Map[String, String]        = Map("parallelism" -> "5")
  val liteStreamInvalidTypeProperties: Map[String, String] = Map("parallelism" -> "non-int")

  // lite request-response
  val requestResponseEmptyTypeData: RequestResponseMetaData = RequestResponseMetaData(None)
  val requestResponseFullTypeData: RequestResponseMetaData  = RequestResponseMetaData(slug = Some("exampleSlug"))
  val requestResponseEmptyProperties: Map[String, String]   = Map("slug" -> "")
  val requestResponseFullProperties: Map[String, String]    = Map("slug" -> "exampleSlug")

  // fragment
  val fragmentEmptyTypeData: FragmentSpecificData = FragmentSpecificData(None, None)
  val fragmentFullTypeData: FragmentSpecificData =
    FragmentSpecificData(docsUrl = Some("exampleUrl"), componentGroup = Some("someGroup"))
  val fragmentEmptyProperties: Map[String, String] = Map("docsUrl" -> "", "componentGroup" -> "")
  val fragmentFullProperties: Map[String, String]  = Map("docsUrl" -> "exampleUrl", "componentGroup" -> "someGroup")

  val flinkMetaDataName           = "StreamMetaData"
  val liteStreamMetaDataName      = "LiteStreamMetaData"
  val requestResponseMetaDataName = "RequestResponseMetaData"
  val fragmentMetaDataName        = "FragmentSpecificData"

  val nonTypeSpecificProperties: Map[String, String] = Map(
    "aProperty"     -> "aValue",
    "emptyProperty" -> ""
  )

  val emptyMetaDataCases: TableFor3[Map[String, String], String, TypeSpecificData] = Table(
    ("properties", "metaDataName", "typeSpecificData"),
    (flinkEmptyProperties, flinkMetaDataName, flinkEmptyTypeData),
    (liteStreamEmptyProperties, liteStreamMetaDataName, liteStreamEmptyTypeData),
    (requestResponseEmptyProperties, requestResponseMetaDataName, requestResponseEmptyTypeData),
    (fragmentEmptyProperties, fragmentMetaDataName, fragmentEmptyTypeData)
  )

  val fullMetaDataCases: TableFor3[Map[String, String], String, TypeSpecificData] = Table(
    ("properties", "metaDataName", "typeSpecificData"),
    (flinkFullProperties, flinkMetaDataName, flinkFullTypeData),
    (liteStreamFullProperties, liteStreamMetaDataName, liteStreamFullTypeData),
    (requestResponseFullProperties, requestResponseMetaDataName, requestResponseFullTypeData),
    (fragmentFullProperties, fragmentMetaDataName, fragmentFullTypeData)
  )

  val invalidTypeAndEmptyMetaDataCases: TableFor3[Map[String, String], String, TypeSpecificData] = Table(
    ("properties", "metaDataName", "typeSpecificData"),
    (flinkInvalidTypeProperties, flinkMetaDataName, flinkEmptyTypeData),
    (liteStreamInvalidTypeProperties, liteStreamMetaDataName, liteStreamEmptyTypeData)
  )

}
