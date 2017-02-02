package pl.touk.esp.engine.api.process

import pl.touk.esp.engine.api.test.TestDataParser

trait Source[T] {

}

trait SourceFactory[T] extends Serializable {
  def clazz : Class[_]

  def testDataParser: Option[TestDataParser[T]]

}
