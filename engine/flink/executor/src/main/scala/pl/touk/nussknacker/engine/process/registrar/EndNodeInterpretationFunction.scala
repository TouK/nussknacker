package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.{RichMapFunction, RuntimeContext}
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.{Context, MetaData, ProcessListener}

private[registrar] class EndNodeInterpretationFunction(prepareListeners: RuntimeContext => Seq[ProcessListener],
                                                       nodeId: String,
                                                       processMetaData: MetaData) extends RichMapFunction[Context, Context] {

  private var listeners: Seq[ProcessListener] = _

  override def open(parameters: Configuration): Unit = {
    listeners = prepareListeners(getRuntimeContext)
  }

  override def close(): Unit = {
    if (listeners != null) {
      listeners.foreach(_.close())
    }
  }

  override def map(context: Context): Context = {
    listeners.foreach(_.endEncountered(nodeId, context, processMetaData))
    context
  }

}
