package pl.touk.nussknacker.engine.standalone.utils

import java.nio.charset.StandardCharsets

import argonaut.{DecodeJson, Parse}
import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.process.Source
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.standalone.api.{DecodingError, StandalonePostSource, StandaloneSourceFactory}

import scala.reflect.ClassTag

class JsonStandaloneSourceFactory[T:DecodeJson:ClassTag] extends StandaloneSourceFactory[T] {


  private def parse(str: String): T = Parse.parse(str).right.get.jdecode[T].result match {
    case Left(error) => throw DecodingError(s"Failed to decode on ${error._1}")
    case Right(data) => data
  }

  @MethodToInvoke
  def create(): Source[T] = {
    new StandalonePostSource[T] {

      override def parse(parameters: Array[Byte]): T = {
        JsonStandaloneSourceFactory.this.parse(new String(parameters, StandardCharsets.UTF_8))
      }

    }
  }

  override def clazz: Class[_] = implicitly[ClassTag[T]].runtimeClass

  override def testDataParser: Option[TestDataParser[T]] = Some(
    new TestDataParser[T] {
      override def parseTestData(data: Array[Byte]): List[T] = {
        val requestList = new String(data, StandardCharsets.UTF_8).split("\n").toList
        requestList.map(parse)
      }
    }
  )
}