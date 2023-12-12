package pl.touk.nussknacker.engine.process.typeinformation.internal

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.{ScenarioProcessingContext, ValueWithContext}
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo

object ValueWithContextTypeHelpers {

  def infoFromValueAndContext[T](
      value: TypeInformation[T],
      context: TypeInformation[ScenarioProcessingContext]
  ): TypeInformation[ValueWithContext[T]] =
    ConcreteCaseClassTypeInfo(
      ("value", value),
      ("context", context)
    )

}
