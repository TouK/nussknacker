package pl.touk.nussknacker.engine.flink.api.process

import cats.Id
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.functions.sink.{DiscardingSink, SinkFunction}
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.process.{RunMode, Sink}
import pl.touk.nussknacker.engine.api.{Context, ContextId, LazyParameter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.resultcollector.{CollectableAction, TransmissionNames}

/**
  * Implementations of this trait can use LazyParameters
  */
trait FlinkSink extends Sink {

  def registerSink(dataStream: DataStream[Context],
                   flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_]

}

/**
  * This is basic Flink sink, which just uses single expression from sink definition
  */
trait BasicFlinkSink[T <: AnyRef] extends FlinkSink with ExplicitUidInOperatorsSupport {

  override def registerSink(dataStream: DataStream[Context],
                            flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
    val lph = flinkNodeContext.lazyParameterHelper
    //TODO:
    implicit val ti: TypeInformation[T] = TypeInformation.of(classOf[Any]).asInstanceOf[TypeInformation[T]]

    val nodeId = flinkNodeContext.nodeId
    val collector = flinkNodeContext.resultsCollector

    setUidToNodeIdIfNeed(flinkNodeContext, dataStream
      .flatMap(valueFunction(lph))
      //TODO: make it nicer?
      .flatMap((a: ValueWithContext[T]) => {
        val value = a.value
        val finalOut = collector.collectWithResponse[Option[Any], Id](ContextId(a.context.id), NodeId(nodeId), value,
          Some(None), CollectableAction(() => value, Some(value)), TransmissionNames.default)
        finalOut.map(_.asInstanceOf[T])
      })
      .addSink(if (flinkNodeContext.runMode == RunMode.Test) new DiscardingSink[T] else toFlinkFunction))
  }

  def valueFunction(helper: FlinkLazyParameterFunctionHelper): FlatMapFunction[Context, ValueWithContext[T]]

  def toFlinkFunction: SinkFunction[T]

}