package pl.touk.nussknacker.engine.process.runner

import java.net.ConnectException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import java.util.{Date, UUID}

import com.typesafe.config.Config
import io.circe.Encoder
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.scalatest.{FlatSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.SignalTransformer
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.test.{EmptyLineSplittedTestDataParser, NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api.typed.{ReturningType, ServiceReturningType, TypedMap, typing}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation, FlinkSourceFactory}
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.flink.util.signal.KafkaSignalStreamConnector
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EspDeserializationSchema}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaEspUtils}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.process.ProcessTestHelpers._
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.util.typing.TypingUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

class FlinkProcessMainSpec extends FlatSpec with Matchers with Inside {

  import spel.Implicits._

  it should "be able to compile and serialize services" in {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .filter("filter1", "#sum(#input.![value1]) > 24")
        .processor("proc2", "logService", "all" -> "#distinct(#input.![value2])")
        .emptySink("out", "monitor")

    FlinkTestConfiguration.setQueryableStatePortRangesBySystemProperties()
    FlinkProcessMain.main(Array(ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2, Encoder[ProcessVersion].apply(ProcessVersion.empty).noSpaces))
  }

}

object LogService extends Service {

  val invocationsCount = new AtomicInteger(0)

  def clear() = {
    invocationsCount.set(0)
  }

  @MethodToInvoke
  def invoke(@ParamName("all") all: Any)(implicit ec: ExecutionContext, collector: ServiceInvocationCollector): Future[Unit] = {
    collector.collect(s"$all-collectedDuringServiceInvocation", Option(())) {
      invocationsCount.incrementAndGet()
      Future.successful(())
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
    FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
      context.signalSenderProvider.get[TestProcessSignalFactory]
        .connectWithSignals(start, context.metaData.id, context.nodeId, new EspDeserializationSchema(identity))
        .map((a:Context) => ValueWithContext("", a),
              (_:Array[Byte]) => ValueWithContext[Any]("", Context("id")))
  })
}

object TransformerWithTime extends CustomStreamTransformer {

  override def clearsContext = true

  @SignalTransformer(signalClass = classOf[TestProcessSignalFactory])
  @MethodToInvoke(returnType = classOf[Int])
  def execute(@ParamName("seconds") seconds: Int) =
    FlinkCustomStreamTransformation((start: DataStream[Context], context: FlinkCustomNodeContext) => {
      start
        .map(_ => 1)
        .timeWindowAll(Time.seconds(seconds)).reduce(_ + _)
        .map(ValueWithContext[Any](_, Context(UUID.randomUUID().toString)))
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

  override def services(config: Config) = Map(
    "logService" -> WithCategories(LogService, "c1"),
    "throwingService" -> WithCategories(new ThrowingService(new RuntimeException("Thrown as expected")), "c1"),
    "throwingTransientService" -> WithCategories(new ThrowingService(new ConnectException()), "c1"),
    "returningDependentTypeService" -> WithCategories(ReturningDependentTypeService, "c1")

  )

  override def sinkFactories(config: Config) = Map(
    "monitor" -> WithCategories(SinkFactory.noParam(MonitorEmptySink), "c2"),
    "sinkForInts" -> WithCategories(SinkFactory.noParam(SinkForInts))
  )

  override def listeners(config: Config) = List()

  override def customStreamTransformers(config: Config) = Map("stateCustom" -> WithCategories(StateCustomNode),
          "signalReader" -> WithCategories(CustomSignalReader),
          "transformWithTime" -> WithCategories(TransformerWithTime)
  )

  override def sourceFactories(config: Config) = Map(
    "input" -> WithCategories(TestSources.simpleRecordSource, "cat2"),
    "jsonInput" -> WithCategories(TestSources.jsonSource, "cat2"),
    "typedJsonInput" -> WithCategories(TestSources.typedJsonSource, "cat2")
  )

  override def signals(config: Config) = Map("sig1" ->
          WithCategories(new TestProcessSignalFactory(KafkaConfig("", None, None), "")))


  override def exceptionHandlerFactory(config: Config) =
    ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler)

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
    new CollectionSource[SimpleRecord](new ExecutionConfig, List(), Some(ascendingTimestampExtractor), Typed[SimpleRecord]) with TestDataParserProvider[SimpleRecord] {
      override def testDataParser: TestDataParser[SimpleRecord] = newLineSplittedTestDataParser
    })


  val jsonSource = FlinkSourceFactory.noParam(
    new CollectionSource[SimpleJsonRecord](new ExecutionConfig, List(), None, Typed[SimpleJsonRecord]) with TestDataParserProvider[SimpleJsonRecord] {
      override def testDataParser: TestDataParser[SimpleJsonRecord] = new EmptyLineSplittedTestDataParser[SimpleJsonRecord] {

        override def parseElement(json: String): SimpleJsonRecord = {
          json.decodeOption[SimpleJsonRecord].get
        }

      }
    }
  )

  val typedJsonSource = new FlinkSourceFactory[TypedMap] with ReturningType {

    @MethodToInvoke
    def create(processMetaData: MetaData,  @ParamName("type") definition: java.util.Map[String, _]): Source[TypedMap] = {
      new CollectionSource[TypedMap](new ExecutionConfig, List(), None, Typed[TypedMap]) with TestDataParserProvider[TypedMap] with ReturningType {

        override def testDataParser: TestDataParser[TypedMap] = new EmptyLineSplittedTestDataParser[TypedMap] {
          override def parseElement(json: String): TypedMap = {
            TypedMap(json.decodeOption[Map[String, String]].get)
          }
        }

        override val returnType: typing.TypingResult = TypingUtils.typeMapDefinition(definition)

      }
    }

    override def timestampAssigner: Option[TimestampAssigner[TypedMap]] = None

    override def returnType: typing.TypingResult = Typed[TypedMap]
  }

}

object ReturningDependentTypeService extends Service with ServiceReturningType {

  @MethodToInvoke
  def invoke(@ParamName("definition") definition: java.util.List[String],
             @ParamName("toFill") toFill: String, @ParamName("count") count: Int): Future[java.util.List[_]] = {
    val result = (1 to count)
      .map(line => definition.asScala.map(_ -> toFill).toMap)
      .map(TypedMap(_))
      .toList.asJava
    Future.successful(result)
  }

  //returns list of type defined by definition parameter
  override def returnType(parameters: Map[String, (TypingResult, Option[Any])]): typing.TypingResult = {
    parameters
      .get("definition")
      .flatMap(_._2)
      .map(definition => TypedObjectTypingResult(definition.asInstanceOf[java.util.List[String]].asScala.map(_ -> Typed[String]).toMap))
      .map(param => TypedClass(classOf[java.util.List[_]], List(param)))
      .getOrElse(Unknown)
  }
}