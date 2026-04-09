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
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

import java.util

case object StatefulTransformer extends CustomStreamTransformer with LazyLogging {

  import pl.touk.nussknacker.engine.flink.util.richflink._

  private val groupByParameterName = ParameterName("groupBy")

  @MethodToInvoke
  def execute(@ParamName("groupBy") groupBy: LazyParameter[String]): FlinkCustomStreamTransformation =
    FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) => {
      start
        .groupBy(groupBy, groupByParameterName)(ctx)
        .mapWithState[ValueWithContext[AnyRef], util.List[AnyRef]] { (valueWithContext, oldState) =>
          val input = valueWithContext.context.apply[AnyRef]("input")
          logger.info(s"received: $input, current state: $oldState")
          val nList = oldState.getOrElse(new util.ArrayList[AnyRef]())
          nList.add(input)

          (ValueWithContext(nList, valueWithContext.context), Some(nList))
        }(
          ctx.valueWithContextInfo.forUnknown,
          Types.LIST(TypeInformationDetection.instance.forType(ctx.asOneOutputContext("input")))
        )
    })

}
