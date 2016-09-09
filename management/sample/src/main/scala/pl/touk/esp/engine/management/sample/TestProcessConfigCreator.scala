package pl.touk.esp.engine.management.sample

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.scala._
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.api.Service
import pl.touk.esp.engine.util.exception.VerboselyLoggingExceptionHandler
import scala.concurrent.Future

class TestProcessConfigCreator extends ProcessConfigCreator {

  override def sinkFactories(config: Config) = {
    val sendSmsSink = EmptySink
    val monitorSink = EmptySink
    Map[String, SinkFactory](
      "sendSms" -> SinkFactory.noParam(sendSmsSink),
      "monitor" -> SinkFactory.noParam(monitorSink)
    )
  }

  override def listeners(config: Config) = List()

  override def sourceFactories(config: Config) = {
    Map[String, SourceFactory[_]](
      "kafka-transaction" -> SourceFactory.noParam(prepareNotEndingSource)
    )
  }

  //potrzebujemy czegos takiego bo CollectionSource konczy sie sam i testy mi glupich rzeczy nie wykrywaly :)
  def prepareNotEndingSource: Source[String] = {
    new Source[String] {
      override def typeInformation = implicitly[TypeInformation[String]]

      override def timestampAssigner = None

      override def toFlinkSource = new SourceFunction[String] {
        var running = true

        override def cancel() = {
          running = false
        }

        override def run(ctx: SourceContext[String]) = {
          while (running) {
            Thread.sleep(2000)
          }
        }
      }
    }
  }

  override def services(config: Config) = {
    Map[String, Service](
      "accountService" -> EmptyService,
      "componentService" -> EmptyService,
      "transactionService" -> EmptyService,
      "serviceModelService" -> EmptyService
    )
  }

  override def foldingFunctions(config: Config) = Map()

  override def exceptionHandler(config: Config) = VerboselyLoggingExceptionHandler

}

case object EmptySink extends Sink {
  override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
    override def invoke(value: Any): Unit = {}
  }
}

case object EmptyService extends Service {
  def invoke() = Future.successful(Unit)
}