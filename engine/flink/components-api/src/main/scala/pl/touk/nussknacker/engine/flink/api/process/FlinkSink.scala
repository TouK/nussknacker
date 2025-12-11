package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.connector.sink2.{Sink => SinkV2}
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSink}
import pl.touk.nussknacker.engine.api.{Context, ValueWithContext}
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamSinkExtension
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

/**
  * Implementations of this trait can use LazyParameters
  */
trait FlinkSink extends Sink with Serializable {

  type Value <: AnyRef

  // It has to be function in order to avoid serialization of whole FlinkSink for testing mechanism
  def prepareTestValueFunction: Value => AnyRef = identity

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
trait BasicFlinkSink extends FlinkSink {

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
    dataStream
      .map(
        (k: ValueWithContext[Value]) => k.value,
        TypeInformationDetection.instance.forType(typeResult).asInstanceOf[TypeInformation[Value]]
      )
      .sinkTo(toFlinkSink(flinkNodeContext))
      .setUidAndNameToNodeId(flinkNodeContext.nodeId)

  def valueFunction(
      helper: FlinkLazyParameterFunctionHelper
  ): FlatMapFunction[Context, ValueWithContext[Value]]

  def toFlinkSink(flinkNodeContext: FlinkCustomNodeContext): SinkV2[Value]

}
