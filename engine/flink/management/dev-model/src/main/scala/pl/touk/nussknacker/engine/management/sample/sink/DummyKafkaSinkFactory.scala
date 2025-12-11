package pl.touk.nussknacker.engine.management.sample.sink

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.connector.sink2.{Sink => SinkV2}
import org.apache.flink.streaming.api.functions.sink.v2.DiscardingSink
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.editor.{Editor, EditorType}
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.flink.api.process.{
  BasicFlinkSink,
  FlinkCustomNodeContext,
  FlinkLazyParameterFunctionHelper
}

import javax.validation.constraints.NotBlank

object DummyKafkaSinkFactory extends SinkFactory {

  @MethodToInvoke
  def create(
      @Editor(`type` = EditorType.SPEL_TEMPLATE_EDITOR)
      @Editor(`type` = EditorType.SPEL_EDITOR)
      @ParamName("Topic") @NotBlank topic: String,
      @ParamName("Value") value: LazyParameter[AnyRef]
  ): Sink = new BasicFlinkSink {

    override type Value = AnyRef

    override def valueFunction(
        helper: FlinkLazyParameterFunctionHelper
    ): FlatMapFunction[Context, ValueWithContext[Value]] =
      helper.lazyMapFunction(value)

    override def toFlinkSink(flinkNodeContext: FlinkCustomNodeContext): SinkV2[Value] =
      new DiscardingSink[AnyRef]
  }

}
