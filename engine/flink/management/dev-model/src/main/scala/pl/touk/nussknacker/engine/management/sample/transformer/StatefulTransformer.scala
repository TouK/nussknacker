package pl.touk.nussknacker.engine.management.sample.transformer

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.{TypeHint, TypeInformation}
import org.apache.flink.streaming.api.datastream.DataStream
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, LazyParameter, MethodToInvoke, ParamName, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.typeinformation.ValueWithContextType

case object StatefulTransformer extends CustomStreamTransformer with LazyLogging {

  @MethodToInvoke
  def execute(@ParamName("groupBy") groupBy: LazyParameter[String]): FlinkCustomStreamTransformation
  = FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) => {
    start
      .flatMap(ctx.lazyParameterHelper.lazyMapFunction(groupBy))
      .keyBy((v: ValueWithContext[_]) => v.value)
      .mapWithState[ValueWithContext[AnyRef], List[String]] { case (StringFromIr(ir, sr), oldState) =>
        logger.info(s"received: $sr, current state: $oldState")
        val nList = sr :: oldState.getOrElse(Nil)
        (ValueWithContext(nList, ir.context), Some(nList))
      }(ValueWithContextType.info, TypeInformation.of(new TypeHint[List[String]] {}))
  })

  object StringFromIr {
    def unapply(ir: ValueWithContext[_]): Option[(ValueWithContext[_], String)] = Some(ir, ir.context.apply[String]("input"))
  }

}
