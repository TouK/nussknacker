package pl.touk.nussknacker.engine.flink.util.transformer.aggregate

import org.apache.flink.api.common.functions.RichMapFunction
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkLazyParameterFunctionHelper, LazyParameterInterpreterFunction}
import pl.touk.nussknacker.engine.flink.util.keyed.StringKeyedValue

class StringKeyedValueMapper(protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper, key: LazyParameter[CharSequence], value: LazyParameter[AnyRef])
  extends RichMapFunction[Context, ValueWithContext[StringKeyedValue[AnyRef]]] with LazyParameterInterpreterFunction {

  implicit def lazyParameterInterpreterImpl: LazyParameterInterpreter = lazyParameterInterpreter

  private lazy val interpreter = lazyParameterInterpreter.syncInterpretationFunction(
    key.map[String](Option(_: CharSequence).map(_.toString).orNull).product(value).map(tuple => StringKeyedValue(tuple._1, tuple._2))
  )

  override def map(ctx: Context): ValueWithContext[StringKeyedValue[AnyRef]] = ValueWithContext(interpreter(ctx), ctx)
}
