package pl.touk.esp.engine.management.sample

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.esp.engine.api.{FoldingFunction, Service}
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.api.Service
import pl.touk.esp.engine.api.exception.ExceptionHandlerFactory
import pl.touk.esp.engine.kafka.{KafkaConfig, KafkaSinkFactory}
import pl.touk.esp.engine.util.exception.VerboselyLoggingExceptionHandler

import scala.concurrent.Future

class TestProcessConfigCreator extends ProcessConfigCreator {


  override def sinkFactories(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.zkAddress"), config.getString("kafka.kafkaAddress"), None)

    val sendSmsSink = EmptySink
    val monitorSink = EmptySink
    Map[String, SinkFactory](
      "sendSms" -> SinkFactory.noParam(sendSmsSink),
      "monitor" -> SinkFactory.noParam(monitorSink),
      "kafka-string" -> new KafkaSinkFactory(kConfig.kafkaAddress,
        new KeyedSerializationSchema[Any] {
          override def serializeValue(element: Any) = element.toString.getBytes

          override def serializeKey(element: Any) = null

          override def getTargetTopic(element: Any) = null
        })
    )
  }

  override def listeners(config: Config) = List()

  override def sourceFactories(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.zkAddress"), config.getString("kafka.kafkaAddress"), None)

    Map[String, SourceFactory[_]](
      "kafka-transaction" -> SourceFactory.noParam(prepareNotEndingSource),
      "oneSource" -> SourceFactory.noParam(new Source[String] {

        override def timestampAssigner = None

        override def toFlinkSource = new SourceFunction[String] {

          var run = true

          var emited = false

          override def cancel() = {
            run = false
          }

          override def run(ctx: SourceContext[String]) = {
            while (run) {
              if (!emited) ctx.collect("One element")
              emited = true
              Thread.sleep(1000)
            }
          }
        }

        override def typeInformation = implicitly[TypeInformation[String]]
      })
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

  override def foldingFunctions(config: Config) = Map("sample" -> SampleFoldingFunction)

  override def exceptionHandlerFactory(config: Config) =
    ExceptionHandlerFactory.noParams(VerboselyLoggingExceptionHandler)

}

case object EmptySink extends Sink {
  override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
    override def invoke(value: Any): Unit = {}
  }
}

case object EmptyService extends Service {
  def invoke() = Future.successful(Unit)
}

case class SampleFold(count: Int)

object SampleFoldingFunction extends FoldingFunction[SampleFold] {
  override def fold(value: AnyRef, acc: Option[SampleFold]) = {
    val value = acc.getOrElse(SampleFold(0))
    value.copy(count = value.count + 1)
  }
}