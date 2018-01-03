package pl.touk.nussknacker.engine.process.runner

import java.net.ConnectException
import java.nio.charset.StandardCharsets
import java.util.{Date, UUID}
import java.util.concurrent.atomic.AtomicInteger

import argonaut.PrettyParams
import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.scalatest.{FlatSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.SignalTransformer
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.test.{EmptyLineSplittedTestDataParser, NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation, FlinkSourceFactory}
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.nussknacker.engine.flink.util.exception.{VerboselyLoggingExceptionHandler, VerboselyLoggingRestartingExceptionHandler}
import pl.touk.nussknacker.engine.flink.util.signal.KafkaSignalStreamConnector
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EspDeserializationSchema}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaEspUtils}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.process.ProcessTestHelpers._
import pl.touk.nussknacker.engine.spel

import scala.concurrent.{ExecutionContext, Future}

class FlinkProcessMainSpec extends FlatSpec with Matchers with Inside {

  import spel.Implicits._

  val ProcessMarshaller = new ProcessMarshaller

  it should "be able to compile and serialize services" in {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .filter("filter1", "#sum(#input.![value1]) > 24")
        .processor("proc2", "logService", "all" -> "#distinct(#input.![value2])")
        .emptySink("out", "monitor")

    FlinkProcessMain.main(Array(ProcessMarshaller.toJson(process, PrettyParams.spaces2)))
  }

}

object LogService extends Service {

  val invocationsCount = new AtomicInteger(0)

  def clear() = {
    invocationsCount.set(0)
  }

  @MethodToInvoke
  def invoke(@ParamName("all") all: Any)(implicit ec: ExecutionContext, collector: ServiceInvocationCollector): Future[Unit] = {
    if (collector.collectorEnabled) {
      collector.collect(s"$all-collectedDuringServiceInvocation")
      Future.successful(Unit)
    } else {
      invocationsCount.incrementAndGet()
      Future.successful(Unit)
    }
  }
}

class ThrowingService(exception: Exception) extends Service {
  @MethodToInvoke
  def invoke(@ParamName("throw") throwing: Boolean): Future[Unit] = {
    if (throwing) {
      Future.failed(exception)
    } else  Future.successful(Unit)
  }
}


object CustomSignalReader extends CustomStreamTransformer {

  @SignalTransformer(signalClass = classOf[TestProcessSignalFactory])
  @MethodToInvoke(returnType = classOf[Void])
  def execute() =
    FlinkCustomStreamTransformation((start: DataStream[InterpretationResult], context: FlinkCustomNodeContext) => {
      context.signalSenderProvider.get[TestProcessSignalFactory]
        .connectWithSignals(start, context.metaData.id, context.nodeId, new EspDeserializationSchema(identity))
        .map((a:InterpretationResult) => ValueWithContext(a),
              (_:Array[Byte]) => ValueWithContext[Any]("", Context("id")))
  })
}

object TransformerWithTime extends CustomStreamTransformer {

  override def clearsContext = true

  @SignalTransformer(signalClass = classOf[TestProcessSignalFactory])
  @MethodToInvoke(returnType = classOf[Int])
  def execute(@ParamName("seconds") seconds: Int) =
    FlinkCustomStreamTransformation((start: DataStream[InterpretationResult], context: FlinkCustomNodeContext) => {
      start
        .map(_ => 1)
        .timeWindowAll(Time.seconds(seconds)).reduce(_ + _)
        .map(ValueWithContext(_, Context(UUID.randomUUID().toString)))
  })
}



class TestProcessSignalFactory(val kafkaConfig: KafkaConfig, val signalsTopic: String)
  extends FlinkProcessSignalSender with KafkaSignalStreamConnector {

  @MethodToInvoke
  def sendSignal()(processId: String): Unit = {
    KafkaEspUtils.sendToKafkaWithTempProducer(signalsTopic, Array.empty[Byte], "".getBytes(StandardCharsets.UTF_8))(kafkaConfig)
  }

}



class SimpleProcessConfigCreator extends ProcessConfigCreator {

  import org.apache.flink.streaming.api.scala._

  override def services(config: Config) = Map(
    "logService" -> WithCategories(LogService, "c1"),
    "throwingService" -> WithCategories(new ThrowingService(new RuntimeException("Thrown as expected")), "c1"),
    "throwingTransientService" -> WithCategories(new ThrowingService(new ConnectException()), "c1")

  )

  override def sinkFactories(config: Config) = Map(
    "monitor" -> WithCategories(new SinkFactory {
      @MethodToInvoke
      def create(): Sink = MonitorEmptySink
    }, "c2"),
    "sinkForInts" -> WithCategories(SinkFactory.noParam(SinkForInts))
  )

  override def listeners(config: Config) = List()

  override def customStreamTransformers(config: Config) = Map("stateCustom" -> WithCategories(StateCustomNode),
          "signalReader" -> WithCategories(CustomSignalReader),
          "transformWithTime" -> WithCategories(TransformerWithTime)
  )

  override def sourceFactories(config: Config) = Map(
    "input" -> WithCategories(TestSources.simpleRecordSource, "cat2"),
    "jsonInput" -> WithCategories(TestSources.jsonSource, "cat2")
  )

  override def signals(config: Config) = Map("sig1" ->
          WithCategories(new TestProcessSignalFactory(KafkaConfig("", "", None, None), "")))


  override def exceptionHandlerFactory(config: Config) =
    ExceptionHandlerFactory.noParams(VerboselyLoggingRestartingExceptionHandler(_))

  override def expressionConfig(config: Config) = ExpressionConfig(Map.empty, List.empty)

  override def buildInfo(): Map[String, String] = Map.empty
}

object TestSources {
  import org.apache.flink.streaming.api.scala._

  import argonaut._
  import argonaut.Argonaut._
  import ArgonautShapeless._

  private val ascendingTimestampExtractor = new AscendingTimestampExtractor[SimpleRecord] {
    override def extractAscendingTimestamp(element: SimpleRecord) = element.date.getTime
  }

  private val newLineSplittedTestDataParser = new NewLineSplittedTestDataParser[SimpleRecord] {
    override def parseElement(csv: String): SimpleRecord = {
      val parts = csv.split("\\|")
      SimpleRecord(parts(0), parts(1).toLong, parts(2), new Date(parts(3).toLong), Some(BigDecimal(parts(4))), BigDecimal(parts(5)), parts(6))
    }
  }

  val simpleRecordSource = FlinkSourceFactory.noParam(
    new CollectionSource[SimpleRecord](new ExecutionConfig, List(),
      Some(ascendingTimestampExtractor)
  ), Some(newLineSplittedTestDataParser))


  val jsonSource = FlinkSourceFactory.noParam(
    new CollectionSource[SimpleJsonRecord](new ExecutionConfig, List(), None), Some(new EmptyLineSplittedTestDataParser[SimpleJsonRecord] {

      override def parseElement(json: String): SimpleJsonRecord = {
        json.decodeOption[SimpleJsonRecord].get
      }
    })
  )

}