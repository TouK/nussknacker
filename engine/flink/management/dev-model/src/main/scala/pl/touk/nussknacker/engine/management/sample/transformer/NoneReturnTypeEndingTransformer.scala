package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.streaming.api.datastream.DataStream
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}

case object NoneReturnTypeEndingTransformer extends CustomStreamTransformer {

  override def canBeEnding: Boolean = true

  @MethodToInvoke(returnType = classOf[Void])
  def execute(): FlinkCustomStreamTransformation =
    FlinkCustomStreamTransformation((start: DataStream[Context], ctx: FlinkCustomNodeContext) =>
      start.map(
        (context: Context) => ValueWithContext[AnyRef](null, context),
        ctx.valueWithContextInfo.forNull
      )
    )

}
