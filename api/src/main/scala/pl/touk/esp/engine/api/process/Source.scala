package pl.touk.esp.engine.api.process

trait Source[T] {

}

trait SourceFactory[T] extends Serializable {
  def clazz : Class[_]
}