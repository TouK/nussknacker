package pl.touk.nussknacker.engine.requestresponse.utils

import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.test.{TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.TypedMap

import scala.jdk.CollectionConverters._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class QueryStringTestDataParser extends TestRecordParser[TypedMap] {

  override def parse(testRecords: List[TestRecord]): List[TypedMap] = testRecords.map { testRecord =>
    val queryString = CirceUtil.decodeJsonUnsafe[String](testRecord.json)
    val paramMap = queryString
      .split("&")
      .map { param =>
        param.split("=").toList match {
          case name :: value :: Nil => (name, value)
          case _ => throw new IllegalArgumentException(s"Failed to parse $queryString as query string")
        }
      }
      .toList
      .groupBy(_._1)
      .mapValuesNow {
        case oneElement :: Nil => oneElement._2
        case more              => more.map(_._2).asJava
      }
    // TODO: validation??
    TypedMap(paramMap)
  }

}
