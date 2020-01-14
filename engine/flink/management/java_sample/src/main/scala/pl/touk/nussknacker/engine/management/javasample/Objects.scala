package pl.touk.nussknacker.engine.management.javasample

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process.{SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkSource, FlinkSourceFactory}
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink

class Objects extends Serializable {

  def source : WithCategories[SourceFactory[_]] = WithCategories(FlinkSourceFactory.noParam(new FlinkSource[Model] {

    override def toFlinkSource: SourceFunction[Model] = new SourceFunction[Model] {

      override def cancel(): Unit = {}

      override def run(ctx: SourceFunction.SourceContext[Model]): Unit = {
        while (true) {
          Thread.sleep(10000)
        }
      }
    }

    override val typeInformation: TypeInformation[Model] = implicitly[TypeInformation[Model]]

    override def timestampAssigner: Option[TimestampAssigner[Model]] = None
  }))

  def sink = WithCategories(SinkFactory.noParam(EmptySink))

  def exceptionHandler: ExceptionHandlerFactory = ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

}
