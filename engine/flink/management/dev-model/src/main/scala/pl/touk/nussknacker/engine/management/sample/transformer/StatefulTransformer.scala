package pl.touk.nussknacker.engine.management.sample.transformer

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, LazyParameter, MethodToInvoke, ParamName, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import org.apache.flink.streaming.api.scala._

case object StatefulTransformer extends CustomStreamTransformer with LazyLogging {

  @MethodToInvoke
  def execute(@ParamName("groupBy") groupBy: LazyParameter[String]): FlinkCustomStreamTransformation
  = FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) => {
    start
      .flatMap(ctx.lazyParameterHelper.lazyMapFunction(groupBy))
      .keyBy(_.value)
      .mapWithState[ValueWithContext[AnyRef], List[String]] { case (StringFromIr(ir, sr), oldState) =>
        logger.info(s"received: $sr, current state: $oldState")
        val nList = sr :: oldState.getOrElse(Nil)
        (ValueWithContext(nList, ir.context), Some(nList))
      }
  })

  object StringFromIr {
    def unapply(ir: ValueWithContext[_]): Option[(ValueWithContext[_], String)] = Some(ir, ir.context.apply[String]("input"))
  }

}
