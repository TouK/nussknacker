package pl.touk.nussknacker.engine.flink.util

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.util.KeyedValue

// Must be in object because of Java interop (problems with package object) and abstract type StringKeyedValue[V]
object keyValueHelperTypeInformation {
  // It is helper function for interop with java - e.g. in case when you want to have KeyedEvent[POJO, POJO]
  def typeInformation[K, V](keyTypeInformation: TypeInformation[K], valueTypeInformation: TypeInformation[V]): TypeInformation[KeyedValue[K, V]] = {
    implicit val implicitKeyTypeInformation: TypeInformation[K] = keyTypeInformation
    implicit val implicitValueTypeInformation: TypeInformation[V] = valueTypeInformation
    implicitly[TypeInformation[KeyedValue[K, V]]]
  }

  // It is helper function for interop with java - e.g. in case when you want to have StringKeyedEvent[POJO]
  def typeInformation[V](valueTypeInformation: TypeInformation[V]): TypeInformation[KeyedValue[String, V]] = {
    keyValueHelperTypeInformation.typeInformation(implicitly[TypeInformation[String]], valueTypeInformation)
  }
}
