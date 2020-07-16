package pl.touk.nussknacker.engine.flink.util.keyed

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.runtime.state.Keyed

case class StringKeyedValue[V](key: String, value: V) extends Keyed[String] {

  override def getKey: String = key

  def tupled: (String, V) = (key, value)

  def map[N](f: V => N): StringKeyedValue[N] =
    copy(value = f(value))

}

object StringKeyedValue {

  // It is helper function for interop with java - e.g. in case when you want to have StringKeyedEvent[POJO]
  def typeInformation[V](valueTypeInformation: TypeInformation[V]): TypeInformation[StringKeyedValue[V]] = {
    implicit val implicitValueTypeInformation: TypeInformation[V] = valueTypeInformation
    implicitly[TypeInformation[StringKeyedValue[V]]]
  }

}