package pl.touk.nussknacker.engine.requestresponse.utils

import pl.touk.nussknacker.engine.api.test.{TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.TypedMap

import scala.collection.JavaConverters._

class QueryStringTestDataParser extends TestRecordParser[TypedMap] {
  override def parse(testRecord: TestRecord): TypedMap = {
    val queryString = testRecord.asJsonString
    val paramMap = queryString.split("&").map { param =>
      param.split("=").toList match {
        case name :: value :: Nil => (name, value)
        case _ => throw new IllegalArgumentException(s"Failed to parse $queryString as query string")
      }
    }.toList.groupBy(_._1).mapValues {
      case oneElement :: Nil => oneElement._2
      case more => more.map(_._2).asJava
    }
    //TODO: validation??
    TypedMap(paramMap)
  }
}
