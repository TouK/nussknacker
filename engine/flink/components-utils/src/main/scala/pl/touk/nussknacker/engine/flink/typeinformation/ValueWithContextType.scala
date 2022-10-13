package pl.touk.nussknacker.engine.flink.typeinformation

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext

import scala.reflect.ClassTag

object ValueWithContextType {
  def infoFromValueAndContext[T](value: TypeInformation[T], context: TypeInformation[Context]): TypeInformation[ValueWithContext[T]] =
    ConcreteCaseClassTypeInfo (
      ("value", value),
      ("context", context)
    )

  def infoFromValue[T](value: TypeInformation[T]): TypeInformation[ValueWithContext[T]] =
    infoFromValueAndContext(value, ContextType.infoGeneric)

  def infoGeneric: TypeInformation[ValueWithContext[AnyRef]] =
    infoFromValue(TypeInformation.of(classOf[AnyRef]))

  def info[T](nodeCtx: FlinkCustomNodeContext, value: TypingResult): TypeInformation[ValueWithContext[T]] =
    nodeCtx.typeInformationDetection.forValueWithContext(nodeCtx.validationContext.left.get, value)

  def info[T](nodeCtx: FlinkCustomNodeContext, value: TypeInformation[T]): TypeInformation[ValueWithContext[T]] =
    infoFromValueAndContext(value, ContextType.info(nodeCtx))

  def info[T: ClassTag](nodeCtx: FlinkCustomNodeContext): TypeInformation[ValueWithContext[T]] =
    info(nodeCtx, Typed[T])
}
