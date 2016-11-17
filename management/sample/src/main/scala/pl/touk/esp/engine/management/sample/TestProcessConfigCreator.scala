package pl.touk.esp.engine.management.sample

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.exception.ExceptionHandlerFactory
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.flink.api.process.{FlinkSink, FlinkSource, FlinkSourceFactory}
import pl.touk.esp.engine.flink.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.esp.engine.kafka.{KafkaConfig, KafkaSinkFactory}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class TestProcessConfigCreator extends ProcessConfigCreator {


  override def sinkFactories(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.zkAddress"), config.getString("kafka.kafkaAddress"), None)

    val sendSmsSink = EmptySink
    val monitorSink = EmptySink
    Map(
      "sendSms" -> WithCategories(SinkFactory.noParam(sendSmsSink)),
      "monitor" -> WithCategories(SinkFactory.noParam(monitorSink)),
      "kafka-string" -> WithCategories(new KafkaSinkFactory(kConfig,
        new KeyedSerializationSchema[Any] {
          override def serializeValue(element: Any) = element.toString.getBytes

          override def serializeKey(element: Any) = null

          override def getTargetTopic(element: Any) = null
        })
    ))
  }

  override def listeners(config: Config) = List()

  override def sourceFactories(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.zkAddress"), config.getString("kafka.kafkaAddress"), None)

    Map(
      "kafka-transaction" -> WithCategories(FlinkSourceFactory.noParam(prepareNotEndingSource)),
      "oneSource" -> WithCategories(FlinkSourceFactory.noParam(new FlinkSource[String] {

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
      }))
    )
  }

  //potrzebujemy czegos takiego bo CollectionSource konczy sie sam i testy mi glupich rzeczy nie wykrywaly :)
  def prepareNotEndingSource: FlinkSource[String] = {
    new FlinkSource[String] {
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
    Map(
      "accountService" -> WithCategories(EmptyService),
      "componentService" -> WithCategories(EmptyService),
      "transactionService" -> WithCategories(EmptyService),
      "serviceModelService" -> WithCategories(EmptyService)
    )
  }

  override def customStreamTransformers(config: Config) = Map("stateful" -> WithCategories(StatefulTransformer))

  override def exceptionHandlerFactory(config: Config) =
    ExceptionHandlerFactory.noParams(VerboselyLoggingExceptionHandler)

  override def globalProcessVariables(config: Config) = Map.empty
}

case object StatefulTransformer extends CustomStreamTransformer {

  @MethodToInvoke
  def execute(@ParamName("keyBy") keyBy: LazyInterpreter[String])
  = (start: DataStream[InterpretationResult], timeout: FiniteDuration) => {
    start.keyBy(keyBy.syncInterpretationFunction)
      .mapWithState[Any, List[String]] { case (StringFromIr(ir, sr), oldState) =>
      val nList = sr :: oldState.getOrElse(Nil)
      (ValueWithContext(nList, ir.finalContext), Some(nList))
    }

  }

  object StringFromIr {
    def unapply(ir: InterpretationResult) = Some(ir, ir.finalContext.apply[String]("input"))
  }

}

case object EmptySink extends FlinkSink {
  override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
    override def invoke(value: Any): Unit = {}
  }
}

case object EmptyService extends Service {
  def invoke() = Future.successful(Unit)
}