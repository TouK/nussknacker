package pl.touk.esp.engine.standalone.utils

import argonaut.{DecodeJson, Parse}
import pl.touk.esp.engine.api.MethodToInvoke
import pl.touk.esp.engine.api.process.{Source, StandaloneSourceFactory}
import pl.touk.esp.engine.api.test.TestDataParser

import scala.reflect.ClassTag

class JsonStandaloneSourceFactory[T:DecodeJson:ClassTag] extends StandaloneSourceFactory[T] {

  @MethodToInvoke
  def create(): Source[T] = {
    new Source[T] {}
  }

  //TODO: lepsza obsluga bledow?
  private def parse(str: String): T = Parse.parse(str).right.get.jdecode[T].result.right.get

  override def toObject(obj: Array[Byte]): T = {
    parse(new String(obj))
  }

  override def clazz: Class[_] = implicitly[ClassTag[T]].runtimeClass

  override def testDataParser: Option[TestDataParser[T]] = Some(
    new TestDataParser[T] {
      override def parseTestData(data: Array[Byte]): List[T] = {
        val requestList = new String(data).split("\n").toList
        requestList.map(parse)
      }
    }
  )
}