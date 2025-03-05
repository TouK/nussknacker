package pl.touk.nussknacker.engine.management.sample.transformer

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.streaming.api.datastream.DataStream
import pl.touk.nussknacker.engine.api.{
  Context,
  CustomStreamTransformer,
  LazyParameter,
  MethodToInvoke,
  ParamName,
  ValueWithContext
}
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}

import java.util

case object StatefulTransformer extends CustomStreamTransformer with LazyLogging {

  import pl.touk.nussknacker.engine.flink.util.richflink._

  @MethodToInvoke
  def execute(@ParamName("groupBy") groupBy: LazyParameter[String]): FlinkCustomStreamTransformation =
    FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) => {
      start
        .groupBy(groupBy)(ctx)
        .mapWithState[ValueWithContext[AnyRef], util.List[String]] {
          case (StringFromIr(ir, sr), oldState) =>
            logger.info(s"received: $sr, current state: $oldState")
            val nList = oldState.getOrElse(new util.ArrayList[String]())
            nList.add(sr)

            (ValueWithContext(nList, ir.context), Some(nList))
          case _ => throw new IllegalStateException()
        }(ctx.valueWithContextInfo.forUnknown, Types.LIST(Types.STRING))
    })

  object StringFromIr {
    def unapply(ir: ValueWithContext[_]): Option[(ValueWithContext[_], String)] =
      Some(ir, ir.context.apply[String]("input"))
  }

}
