package pl.touk.esp.engine.api.process

trait Source[T] {

}

trait SourceFactory[T] extends Serializable {
  def clazz : Class[_]

  def testDataParser: Option[String => T]

}
