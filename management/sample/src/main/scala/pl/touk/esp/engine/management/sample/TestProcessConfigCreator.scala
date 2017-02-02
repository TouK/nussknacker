package pl.touk.esp.engine.management.sample

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.util.serialization.KeyedSerializationSchema
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.esp.engine.api.lazyy.UsingLazyValues
import pl.touk.esp.engine.api.process._
import pl.touk.esp.engine.api.test.InvocationCollectors.SinkInvocationCollector
import pl.touk.esp.engine.api.test.NewLineSplittedTestDataParser
import pl.touk.esp.engine.flink.api.process.{FlinkSink, FlinkSource, FlinkSourceFactory}
import pl.touk.esp.engine.flink.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.esp.engine.kafka.{KafkaConfig, KafkaSinkFactory}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class TestProcessConfigCreator extends ProcessConfigCreator {


  override def sinkFactories(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.zkAddress"), config.getString("kafka.kafkaAddress"), None, None)

    val sendSmsSink = EmptySink
    val monitorSink = EmptySink
    Map(
      "sendSms" -> WithCategories(SinkFactory.noParam(sendSmsSink), "Category1"),
      "monitor" -> WithCategories(SinkFactory.noParam(monitorSink), "Category1", "Category2"),
      "kafka-string" -> WithCategories(new KafkaSinkFactory(kConfig,
        new KeyedSerializationSchema[Any] {
          override def serializeValue(element: Any) = element.toString.getBytes

          override def serializeKey(element: Any) = null

          override def getTargetTopic(element: Any) = null
        }), "Category1", "Category2")
    )
  }

  override def listeners(config: Config) = List()

  override def sourceFactories(config: Config) = {
    val kConfig = KafkaConfig(config.getString("kafka.zkAddress"), config.getString("kafka.kafkaAddress"), None, None)

    Map(
      "kafka-transaction" -> WithCategories(FlinkSourceFactory.noParam(prepareNotEndingSource, Some(new NewLineSplittedTestDataParser[String] {
        override def parseElement(testElement: String): String = testElement
      })), "Category1", "Category2"),
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
      }), "Category1", "Category2"),
      "csv-source" -> WithCategories(FlinkSourceFactory.noParam(new FlinkSource[CsvRecord] {
        override def typeInformation = implicitly[TypeInformation[CsvRecord]]

        override def toFlinkSource = new SourceFunction[CsvRecord] {
          override def cancel() = {}

          override def run(ctx: SourceContext[CsvRecord]) = {}
        }

        override def timestampAssigner = None

      }, Some(new NewLineSplittedTestDataParser[CsvRecord] {
        override def parseElement(testElement: String): CsvRecord = CsvRecord(testElement.split("\\|").toList)
      })), "Category1", "Category2")
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
      "accountService" -> WithCategories(EmptyService, "Category1"),
      "componentService" -> WithCategories(EmptyService, "Category1", "Category2"),
      "transactionService" -> WithCategories(EmptyService, "Category1"),
      "serviceModelService" -> WithCategories(EmptyService, "Category1", "Category2"),
      "paramService" -> WithCategories(OneParamService, "Category1"),
      "enricher" -> WithCategories(Enricher, "Category1", "Category2"),
      "multipleParamsService" -> WithCategories(MultipleParamsService, "Category1", "Category2")

    )
  }

  override def customStreamTransformers(config: Config) = Map("stateful" -> WithCategories(StatefulTransformer, "Category1", "Category2"))

  override def exceptionHandlerFactory(config: Config) = ParamExceptionHandler

  override def globalProcessVariables(config: Config) = Map(
    "DATE" -> WithCategories(DateProcessHelper.getClass, "Category1", "Category2")
  )

  override def buildInfo(): Map[String, String] = {
    Map(
      "process-version" -> "0.1",
      "engine-version" -> "0.1"
    )
  }
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

case object ParamExceptionHandler extends ExceptionHandlerFactory {
  def create(@ParamName("param1") param: String, metaData: MetaData): EspExceptionHandler = VerboselyLoggingExceptionHandler(metaData)

}

case object EmptySink extends FlinkSink {

  override def testDataOutput: Option[(Any) => String] = Option(out => out.toString)
  override def toFlinkFunction: SinkFunction[Any] = new SinkFunction[Any] {
    override def invoke(value: Any): Unit = ()
  }
}

case object EmptyService extends Service {
  def invoke() = Future.successful(Unit)
}

case object OneParamService extends Service {
  def invoke(@ParamName("param") param: String) = Future.successful(param)
}

case object Enricher extends Service {
  def invoke(@ParamName("param") param: String) = Future.successful(RichObject(param, 123L, Some("rrrr")))
}

case class RichObject(field1: String, field2: Long, field3: Option[String])

case class CsvRecord(fields: List[String]) extends UsingLazyValues {

  lazy val firstField = fields.head

  lazy val enrichedField = lazyValue[RichObject]("enricher", "param" -> firstField)


}

case object MultipleParamsService extends Service {
  def invoke(@ParamName("foo") foo: String,
             @ParamName("bar") bar: String,
             @ParamName("baz") baz: String,
             @ParamName("quax") quax: String) = Future.successful(Unit)
}

object DateProcessHelper {
  def nowTimestamp(): Long = System.currentTimeMillis()
}