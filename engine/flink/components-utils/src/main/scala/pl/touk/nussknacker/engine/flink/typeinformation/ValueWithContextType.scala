package pl.touk.nussknacker.engine.flink.typeinformation

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}

object ValueWithContextType {
  def info[T](value: TypeInformation[T], context: TypeInformation[Context]): TypeInformation[ValueWithContext[T]] =
    ConcreteCaseClassTypeInfo (
      ("value", value),
      ("context", context)
    )

  def info[T](value: TypeInformation[T]): TypeInformation[ValueWithContext[T]] =
    info(value, ContextType.info)

  def info: TypeInformation[ValueWithContext[AnyRef]] =
    info(TypeInformation.of(classOf[AnyRef]))
}
