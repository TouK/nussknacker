package pl.touk.nussknacker.engine.flink.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeHint, TypeInformation}
import pl.touk.nussknacker.engine.util.KeyedValue

// Must be in object because of Java interop (problems with package object) and abstract type StringKeyedValue[V]
object KeyedValueType {
  // It is helper function for interop with java - e.g. in case when you want to have KeyedEvent[POJO, POJO]
  private def info[K, V](key: TypeInformation[K], value: TypeInformation[V]): TypeInformation[KeyedValue[K, V]] =
    ConcreteCaseClassTypeInfo(
      ("key", key),
      ("value", value)
    )

  // It is helper function for interop with java - e.g. in case when you want to have StringKeyedEvent[POJO]
  def info[V](value: TypeInformation[V]): TypeInformation[KeyedValue[String, V]] = {
    info(TypeInformation.of(classOf[String]), value)
  }

  def infoGeneric: TypeInformation[KeyedValue[String, AnyRef]] =
    info(TypeInformation.of(classOf[AnyRef]))
}
