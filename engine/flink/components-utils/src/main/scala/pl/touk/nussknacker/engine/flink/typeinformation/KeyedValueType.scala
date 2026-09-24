package pl.touk.nussknacker.engine.flink.typeinformation

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.flink.api.typeinformation.NullableTypeInfo
import pl.touk.nussknacker.engine.util.KeyedValue

// Must be in object because of Java interop (problems with package object) and abstract type StringKeyedValue[V]
object KeyedValueType {

  /**
    * A helper function for interop with java - e.g. in case when you want to have KeyedEvent[POJO, POJO].
    *
    * The value is wrapped because [[ConcreteCaseClassTypeInfo]] passes fields straight to their serializers, and
    * Flink's serializers for the primitive wrapper types cannot write the null an `aggregateBy` can evaluate to.
    */
  def info[K, V](key: TypeInformation[K], value: TypeInformation[V]): TypeInformation[KeyedValue[K, V]] =
    ConcreteCaseClassTypeInfo(
      ("key", key),
      ("value", new NullableTypeInfo(value))
    )

  // It is helper function for interop with java - e.g. in case when you want to have StringKeyedEvent[POJO]
  def info[V](value: TypeInformation[V]): TypeInformation[KeyedValue[String, V]] = {
    info(TypeInformation.of(classOf[String]), value)
  }

}
