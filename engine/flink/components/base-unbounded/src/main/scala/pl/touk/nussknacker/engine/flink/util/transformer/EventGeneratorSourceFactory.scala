package pl.touk.nussknacker.engine.flink.util.transformer

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import com.typesafe.scalalogging.LazyLogging
import io.circe.{HCursor, Json}
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.MapFunction
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.VariableConstants.InputVariableName
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedLazyParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.definition.ParameterCategory.Advanced
import pl.touk.nussknacker.engine.api.json.decoders.FromJsonTypingResultBasedDecoder
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder
import pl.touk.nussknacker.engine.api.livedata.{DataRecord, DataRecords, LiveDataProvider}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.{typing, ReturningType}
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{
  StandardTimestampWatermarkHandler,
  TimestampWatermarkHandler
}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.TimestampUtils

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util
import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

object EventGeneratorSourceFactory
    extends EventGeneratorSourceFactory(
      new StandardTimestampWatermarkHandler[ValueWithContext[AnyRef]](
        WatermarkStrategy
          .forMonotonousTimestamps()
          .withTimestampAssigner(
            new MapAscendingTimestampExtractor(MapAscendingTimestampExtractor.DefaultTimestampField)
          )
      )
    )

class EventGeneratorSourceFactory(customTimestampAssigner: TimestampWatermarkHandler[ValueWithContext[AnyRef]])
    extends SourceFactory
    with SingleInputDynamicComponent[FlinkSource]
    with UnboundedStreamComponent {

  override type State = Nothing

  override def nodeDependencies: List[NodeDependency] = List.empty

  private lazy val scheduleParameterName = ParameterName("schedule")

  private lazy val countParameterName = ParameterName("count")

  private lazy val valueParameterName = ParameterName("value")

  private val scheduleParameterDeclaration = ParameterDeclaration
    .mandatory[Duration](scheduleParameterName)
    .withCreator(
      _.copy(
        editors = List(
          new DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS))
        ),
        defaultValue = Some("T(java.time.Duration).parse('PT1M')".spel)
      )
    )

  private val countParameterDeclaration = ParameterDeclaration
    .optional[Int](countParameterName)
    .withCreator(
      _.copy(
        validators = List(MinimalNumberValidator(BigDecimal.valueOf(1))),
        editors = List(
          new DurationParameterEditor(List(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS))
        ),
        defaultValue = Some("1".spel),
        category = Advanced
      )
    )

  private val valueParameterDeclaration = ParameterDeclaration
    .lazyMandatory[AnyRef](valueParameterName)
    .withCreator(
      _.copy(
        editors = List(JsonTemplateParameterEditor, SpelParameterEditor),
        defaultValue = Some(
          "{\n\t\"sampleField\": \"#{ #UTIL.uuid }\",\n\t\"dateTime\": \"#{ #DATE.now }\",\n\t\"type\": \"example\",\n\t\"value\": 100\n}".jsonTemplate
        )
      )
    )

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) =>
      NextParameters(
        List(scheduleParameterDeclaration, countParameterDeclaration, valueParameterDeclaration).map(
          _.createParameter()
        )
      )
    case TransformationStep(
          (`scheduleParameterName`, _) ::
          (`countParameterName`, _) ::
          (`valueParameterName`, DefinedLazyParameter(valueType)) ::
          Nil,
          _
        ) =>
      FinalResults(context.withVariableUnsafe(InputVariableName, valueType))
  }

  @nowarn("cat=deprecation")
  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[Nothing]
  ): FlinkSource = {
    val schedule = scheduleParameterDeclaration.extractValueUnsafe(params)
    val count    = countParameterDeclaration.extractValue(params).getOrElse(1)
    val value    = valueParameterDeclaration.extractValueUnsafe(params)

    new FlinkSource
      with BaseFlinkSource
      with ExplicitUidInOperatorsSupport
      with ReturningType
      with FlinkSourceTestSupport[AnyRef]
      with TestDataGenerator
      with TestWithParametersSupport[AnyRef]
      with LiveDataProvider
      with LazyLogging {

      // The stream is created in the following way:
      // 1. Events are triggered by PeriodicFunction
      // 2. There are N=`count` events generated for each event triggered by PeriodicFunction
      // 3. Empty Context is created for each event
      // 4. The input value is evaluated for each event separately, based on the context
      // 5. Source id and timestamps are being set for each value
      // 6. The generated value is added to the context as `input`
      override final def contextStream(
          env: StreamExecutionEnvironment,
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStream[Context] = {
        // Without this local variable, flatMap function is not serializable
        val localCount = count
        val streamOfRaw =
          env
            .addSource(new PeriodicFunction(schedule))
            .flatMap(
              (_: Unit, out: Collector[String]) => {
                // This empty String is a dummy value. It has to exist for each event, but its value is completely ignored.
                // The type is not important too, it just has to be serializable by Flink.
                // For each of those dummy values, a new Context will be created.
                (1 to localCount).foreach(_ => out.collect(""))
              },
              TypeInformationDetection.instance.forClass[String]
            )
            .map(
              new FlinkContextInitializingFunction[String](
                contextInitializer,
                flinkNodeContext.nodeId,
                flinkNodeContext.convertToEngineRuntimeContext
              ),
              flinkNodeContext.contextTypeInfo
            )
            .flatMap(flinkNodeContext.lazyParameterHelper.lazyMapFunction(value))

        val rawSourceWithUidAndTimestamp = sourceWithUidAndTimestamp(
          streamOfRaw,
          flinkNodeContext,
          Some(customTimestampAssigner),
        )

        rawSourceWithUidAndTimestamp.map(new ContextWithInputVariable)
      }

      // This is a custom ContextInitializer, which initializes Context ignoring input.
      // It is required, because in EventGenerator we first initialize Context, and only then generate input.
      private def contextInitializer[T]: ContextInitializer[T] = new ContextInitializer[T] {
        override def convertToInitialVariables(raw: T): ContextVariables = {
          ContextVariables(Map.empty)
        }

        override def validationContext(
            context: ValidationContext
        )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] =
          Valid(context)
      }

      override val returnType: typing.TypingResult = value.returnType

      override def fetchLiveData(maxNumberOfRecords: Int): DataRecords = {
        val records = List.fill(maxNumberOfRecords)(generateSample())
        DataRecords(records.map { record =>
          val timestamp = MapAscendingTimestampExtractor.default.extractTimestampFromRecord(record)
          DataRecord(
            variables = Map(VariableConstants.InputVariableName -> record),
            timestamp = timestamp
          )
        })
      }

      override def generateTestData(size: Int): TestData = {
        val samples = List.fill(size)(encodeValueUnsafe(generateSample()))
        TestData(samples.map(TestRecord(_, None)))
      }

      override def testRecordParser: TestRecordParser[AnyRef] =
        _.map(_.json).map(decodeValueUnsafe)

      override def testParametersDefinition: List[Parameter] = List.empty

      override def parametersToTestData(
          params: Map[ParameterName, AnyRef],
      ): AnyRef = generateSample()

      override def timestampAssignerForTest: Option[TimestampWatermarkHandler[AnyRef]] = None

      private def generateSample(): AnyRef = value.evaluate(Context.dummy)

      private def encodeValueUnsafe(value: AnyRef) =
        ToJsonEncoder.default
          .encode(value)
          .getOrElse(throw new IllegalArgumentException(s"Failed to encode value: $value"))

      private def decodeValueUnsafe(json: Json) =
        FromJsonTypingResultBasedDecoder
          .decodeValue(returnType, HCursor.fromJson(json))
          .getOrElse(throw new IllegalArgumentException(s"Failed to decode value from json: $json"))
          .asInstanceOf[AnyRef]
    }
  }

}

@nowarn("cat=deprecation")
class PeriodicFunction(period: Duration) extends SourceFunction[Unit] {

  @volatile private var isRunning = true

  override def run(ctx: SourceFunction.SourceContext[Unit]): Unit = {
    while (isRunning) {
      ctx.collect(())
      Thread.sleep(period.toMillis)
    }
  }

  override def cancel(): Unit = {
    isRunning = false
  }

}

class MapAscendingTimestampExtractor(timestampField: String)
    extends SerializableTimestampAssigner[ValueWithContext[AnyRef]] {

  override def extractTimestamp(valueWithContext: ValueWithContext[AnyRef], recordTimestamp: Long): Long = {
    val value = valueWithContext.value
    extractTimestampFromRecord(value) getOrElse System.currentTimeMillis()
  }

  def extractTimestampFromRecord(value: AnyRef): Option[Long] = {
    for {
      javaMap <- value match {
        case m: util.Map[String @unchecked, AnyRef @unchecked] => Some(m)
        case _                                                 => None
      }
      timestampFieldValue <- javaMap.asScala.get(timestampField)
      timestampMillis     <- TimestampUtils.supportedTypeToMillis.lift(timestampFieldValue)
    } yield timestampMillis
  }

}

class ContextWithInputVariable extends MapFunction[ValueWithContext[AnyRef], Context] with Serializable {
  override def map(vwc: ValueWithContext[AnyRef]): Context = vwc.context.withVariable(InputVariableName, vwc.value)
}

object MapAscendingTimestampExtractor {
  val DefaultTimestampField = "timestamp"

  val default = new MapAscendingTimestampExtractor(DefaultTimestampField)

}
