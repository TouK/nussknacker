package pl.touk.nussknacker.engine.process.util

import pl.touk.nussknacker.engine.process.util.Serializers.SerializerWithSpecifiedClass
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader

trait SerializersProvider {
  def serializers(): List[SerializerWithSpecifiedClass[_]]
}

object SerializersProvider {
  def load(): List[SerializerWithSpecifiedClass[_]] = ScalaServiceLoader
    .load[SerializersProvider](getClass.getClassLoader)
    .flatMap(_.serializers())
}