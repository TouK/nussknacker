package pl.touk.esp.engine.api.process

import pl.touk.esp.engine.api.test.TestDataParser

trait Source[T] {

}

trait TestDataGenerator {
  def generateTestData(size: Int) : Array[Byte]
}



trait SourceFactory[T] extends Serializable {
  def clazz : Class[_]

  def testDataParser: Option[TestDataParser[T]]

}

trait StandaloneSourceFactory[T] extends SourceFactory[T] {
  def toObject(obj: Array[Byte]): T
}