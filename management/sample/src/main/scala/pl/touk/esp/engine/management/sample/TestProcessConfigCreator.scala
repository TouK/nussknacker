package pl.touk.esp.engine.management.sample

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.scala._
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, Sink, SinkFactory, SourceFactory}
import pl.touk.esp.engine.api.{BrieflyLoggingExceptionHandler, MetaData, Service}
import pl.touk.esp.engine.process.util.CollectionSource

import scala.concurrent.{ExecutionContext, Future}

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
      "kafka-transaction" -> new SourceFactory[String] {
        def create(processMetaData: MetaData) = {
          CollectionSource(new ExecutionConfig(), List("blee"), None)
        }
      }
    )
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

  override def exceptionHandler(config: Config) = BrieflyLoggingExceptionHandler

}

case object EmptySink extends Sink {
  override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
    override def invoke(value: Any): Unit = {}
  }
}

case object EmptyService extends Service {
  def invoke() = Future.successful(Unit)
}