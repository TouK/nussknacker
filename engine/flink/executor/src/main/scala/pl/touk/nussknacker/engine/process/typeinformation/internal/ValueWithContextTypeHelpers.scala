package pl.touk.nussknacker.engine.process.typeinformation.internal

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo

object ValueWithContextTypeHelpers {
  def infoFromValueAndContext[T](value: TypeInformation[T], context: TypeInformation[Context]): TypeInformation[ValueWithContext[T]] =
    ConcreteCaseClassTypeInfo (
      ("value", value),
      ("context", context)
    )

}
