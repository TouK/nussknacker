package pl.touk.esp.engine.api.process

import org.apache.flink.api.common.functions.util.FunctionUtils
import org.apache.flink.api.common.functions.{RichMapFunction, RuntimeContext}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.esp.engine.api.MetaData

import scala.concurrent.{ExecutionContext, Future}

trait Sink {

  def toFlinkFunction: RichMapFunction[InputWithExectutionContext, Future[Unit]]

}

object Sink {

  def fromFlinkSink(flinkSink: SinkFunction[Any]) = new Sink {
    override def toFlinkFunction = new SinkWrapperFunction(flinkSink)
  }

  class SinkWrapperFunction(flinkSink: SinkFunction[Any])
    extends RichMapFunction[InputWithExectutionContext, Future[Unit]] {
    
    override def setRuntimeContext(t: RuntimeContext): Unit = {
      super.setRuntimeContext(t)
      FunctionUtils.setFunctionRuntimeContext(flinkSink, t)
    }

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      FunctionUtils.openFunction(flinkSink, parameters)
    }

    override def map(value: InputWithExectutionContext): Future[Unit] = {
      Future.successful(flinkSink.invoke(value.input))
    }

    override def close(): Unit = {
      super.close()
      FunctionUtils.closeFunction(flinkSink)
    }
  }

}

case class InputWithExectutionContext(input: Any,
                                      executionContext: ExecutionContext)

trait SinkFactory {

  def create(processMetaData: MetaData, parameters: Map[String, String]): Sink

}

object SinkFactory {

  def noParam(sink: Sink): SinkFactory = new SinkFactory {
    override def create(processMetaData: MetaData, parameters: Map[String, String]): Sink = sink
  }

}