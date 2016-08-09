package pl.touk.esp.process.sample

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.scala._

import pl.touk.esp.engine.api.process.{ProcessConfigCreator, SinkFactory, SourceFactory}
import pl.touk.esp.engine.api.{SkipExceptionHandler, MetaData, Service}
import pl.touk.esp.engine.process.util.CollectionSource
import pl.touk.esp.engine.util.sink.ServiceSink

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class TestProcessConfigCreator extends ProcessConfigCreator {

  override def sinkFactories(config: Config) = {
    val sendSmsSink = new ServiceSink(EmptyService, invocationTimeout = 2 minutes)
    val monitorSink = new ServiceSink(EmptyService, invocationTimeout = 2 minutes)
    Map[String, SinkFactory](
      "sendSms" -> SinkFactory.noParam(sendSmsSink),
      "monitor" -> SinkFactory.noParam(monitorSink)
    )
  }

  override def listeners(config: Config) = List()

  override def sourceFactories(config: Config) = {
    Map[String, SourceFactory[_]](
      "kafka-transaction" -> new SourceFactory[String] {
        override def create(processMetaData: MetaData, parameters: Map[String, String]) = {
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

  override def exceptionHandler(config: Config) = SkipExceptionHandler
}

case object EmptyService extends Service {
  override def invoke(params: Map[String, Any])(implicit ec: ExecutionContext) = {
    Future(println(params))
  }
}