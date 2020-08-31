package pl.touk.nussknacker.engine.management.sample.transformer

import org.apache.flink.streaming.api.datastream.DataStreamSink
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.InterpretationResult
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkSink}

object DynamicParametersSink extends SinkFactory with DynamicParametersMixin {

  override def requiresOutput: Boolean = false

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[State]): AnyRef = {
    new FlinkSink with Serializable {
      private val allParams = params

      override def registerSink(dataStream: DataStream[InterpretationResult],
                                flinkNodeContext: FlinkCustomNodeContext): DataStreamSink[_] = {
        dataStream
          .map(_ => allParams.toString())
          .print()
      }

      override def testDataOutput: Option[Any => String] = None
    }
  }

}
