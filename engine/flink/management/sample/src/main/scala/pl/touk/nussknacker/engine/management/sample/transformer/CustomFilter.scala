package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, LazyParameter, MethodToInvoke, ParamName, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation, LazyParameterFilterFunction}
import org.apache.flink.streaming.api.scala._

case object CustomFilter extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Void])
  def execute(@ParamName("expression") expression: LazyParameter[java.lang.Boolean]): FlinkCustomStreamTransformation
  = FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) =>
    start
      .filter(ctx.lazyParameterHelper.lazyFilterFunction(expression))
      .map(ValueWithContext[AnyRef](null, _)))

}
