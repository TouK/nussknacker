package pl.touk.nussknacker.engine.flink.typeinformation

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.ValueWithContext
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.flink.api.process.FlinkCustomNodeContext

import scala.reflect.ClassTag

object ValueWithContextType {
  def infoWithCustomContext[T](nodeCtx: FlinkCustomNodeContext, ctx: ValidationContext, value: TypingResult): TypeInformation[ValueWithContext[T]] =
    nodeCtx.typeInformationDetection.forValueWithContext(ctx, value)

  def infoBranch[T](nodeCtx: FlinkCustomNodeContext, key: String, value: TypingResult): TypeInformation[ValueWithContext[T]] =
    infoWithCustomContext(nodeCtx, nodeCtx.validationContext.right.get(key), value)

  def infoBranch[T: ClassTag](nodeCtx: FlinkCustomNodeContext, key: String): TypeInformation[ValueWithContext[T]] =
    infoBranch(nodeCtx, key, Typed[T])

  def info[T](nodeCtx: FlinkCustomNodeContext, value: TypingResult): TypeInformation[ValueWithContext[T]] =
    infoWithCustomContext(nodeCtx, nodeCtx.validationContext.left.get, value)

  def info[T: ClassTag](nodeCtx: FlinkCustomNodeContext): TypeInformation[ValueWithContext[T]] =
    info(nodeCtx, Typed[T])
}
