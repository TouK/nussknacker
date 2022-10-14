package pl.touk.nussknacker.engine.flink.typeinformation

import org.apache.flink.api.common.typeinfo.TypeInformation

object TupleType {
  def tuple2Info[T, S](first: TypeInformation[T], second: TypeInformation[S]): TypeInformation[(T, S)] = ConcreteCaseClassTypeInfo(
    ("_1", first),
    ("_2", second)
  )
}
