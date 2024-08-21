package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink}
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport

/**
  * Implementations of this trait can use LazyParameters
  */
trait FlinkSink extends Sink with Serializable {

  type Value <: AnyRef

  def prepareTestValue(value: Value): AnyRef = value

  def prepareValue(
      dataStream: DataStream[Context],
      flinkCustomNodeContext: FlinkCustomNodeContext
  ): DataStream[ValueWithContext[Value]]

  def registerSink(
      dataStream: DataStream[ValueWithContext[Value]],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSink[_]

}

/**
  * This is basic Flink sink, which just uses single expression from sink definition
  */
trait BasicFlinkSink extends FlinkSink with ExplicitUidInOperatorsSupport {

  def typeResult: TypingResult = Unknown

  override def prepareValue(
      dataStream: DataStream[Context],
      flinkCustomNodeContext: FlinkCustomNodeContext
  ): DataStream[ValueWithContext[Value]] =
    dataStream.flatMap(
      valueFunction(flinkCustomNodeContext.lazyParameterHelper),
      flinkCustomNodeContext.valueWithContextInfo.forType(typeResult)
    )

  override def registerSink(
      dataStream: DataStream[ValueWithContext[Value]],
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSink[_] =
    setUidToNodeIdIfNeed(
      flinkNodeContext,
      dataStream
        .map(
          (k: ValueWithContext[Value]) => k.value,
          flinkNodeContext.typeInformationDetection.forType(typeResult).asInstanceOf[TypeInformation[Value]]
        )
        .addSink(toFlinkFunction(flinkNodeContext))
    )

  def valueFunction(
      helper: FlinkLazyParameterFunctionHelper
  ): FlatMapFunction[Context, ValueWithContext[Value]]

  def toFlinkFunction(flinkNodeContext: FlinkCustomNodeContext): SinkFunction[Value]
}
