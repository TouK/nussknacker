package pl.touk.nussknacker.engine.management.sample.transformer

import cats.data.Validated.Valid
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api
import pl.touk.nussknacker.engine.api.{Context, CustomStreamTransformer, MethodToInvoke, ValueWithContext}
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}

// copy of CustomTimestampExtractingTransformation
object CustomTimestampExtractingTransformationCopy extends CustomStreamTransformer with Serializable {

  @MethodToInvoke(returnType = classOf[Void])
  def execute(): ContextTransformation = {
    ContextTransformation
      .definedBy(Valid(_))
      .implementedBy(FlinkCustomStreamTransformation { (start: DataStream[Context], context: FlinkCustomNodeContext) =>
        start
          .process(new ProcessFunction[Context, Context] {
            override def processElement(
                value: api.Context,
                ctx: ProcessFunction[api.Context, api.Context]#Context,
                out: Collector[api.Context]
            ): Unit = {
              out.collect(
                value.withVariable("eventTimeTimestampCopy", ctx.timestamp())
              )
            }
          })
          .map(ValueWithContext[AnyRef](null, _), context.valueWithContextInfo.forUnknown)
      })
  }

}
