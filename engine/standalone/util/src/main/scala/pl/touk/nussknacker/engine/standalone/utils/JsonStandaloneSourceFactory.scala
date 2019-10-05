package pl.touk.nussknacker.engine.standalone.utils

import java.nio.charset.StandardCharsets

import io.circe.Decoder
import pl.touk.nussknacker.engine.api.{CirceUtil, MethodToInvoke}
import pl.touk.nussknacker.engine.api.process.{Source, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.standalone.api.{DecodingError, StandalonePostSource, StandaloneSourceFactory}

import scala.reflect.ClassTag

class JsonStandaloneSourceFactory[T:Decoder:ClassTag] extends StandaloneSourceFactory[T] {

  @MethodToInvoke
  def create(): Source[T] = {
    new StandalonePostSource[T] with TestDataParserProvider[T] {

      override def parse(parameters: Array[Byte]): T = {
        parse(new String(parameters, StandardCharsets.UTF_8))
      }

      override def testDataParser: TestDataParser[T] = new TestDataParser[T] {
        override def parseTestData(data: Array[Byte]): List[T] = {
          val requestList = new String(data, StandardCharsets.UTF_8).split("\n").toList
          requestList.map(parse)
        }
      }

      private def parse(str: String): T = CirceUtil.decodeJson[T](str) match {
        case Left(error) => throw DecodingError(s"Failed to decode on ${error.getMessage}", error)
        case Right(data) => data
      }
    }
  }

  override def clazz: Class[_] = implicitly[ClassTag[T]].runtimeClass

}