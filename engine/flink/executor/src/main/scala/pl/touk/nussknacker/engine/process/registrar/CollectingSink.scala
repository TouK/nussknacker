package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.connector.sink2.{Sink, SinkWriter, WriterInitContext}
import pl.touk.nussknacker.engine.api.{NodeId, NodeName, ValueWithContext}
import pl.touk.nussknacker.engine.api.component.{ComponentType, NodeComponentInfo}
import pl.touk.nussknacker.engine.flink.api.WriterInitCtx
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.testmode.SinkInvocationCollector

private[registrar] class CollectingSink[T](
    val compilerDataForClassloader: ClassLoader => FlinkProcessCompilerData,
    collectingSink: SinkInvocationCollector,
    sinkId: NodeId,
    sinkName: NodeName
) extends Sink[ValueWithContext[T]] {

  override def createWriter(context: WriterInitContext): SinkWriter[ValueWithContext[T]] = {
    val compilerData     = compilerDataForClassloader(context.getUserCodeClassLoader.asClassLoader())
    val exceptionHandler = compilerData.prepareExceptionHandler(WriterInitCtx(context))
    new SinkWriter[ValueWithContext[T]] {
      override def write(value: ValueWithContext[T], context: SinkWriter.Context): Unit = {
        exceptionHandler.handling(
          Some(NodeComponentInfo(sinkId, sinkName, ComponentType.Sink, "collectingSink")),
          value.context
        ) {
          collectingSink.collect(value.context, value.value)
        }
      }

      override def flush(endOfInput: Boolean): Unit = {}

      override def close(): Unit = {
        exceptionHandler.close()
      }
    }
  }

}
