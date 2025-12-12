package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.scalalogging.LazyLogging
import io.circe.{HCursor, Json}
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.functions.{OpenContext, RuntimeContext}
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.api.connector.source.util.ratelimit.{RateLimiter, RateLimiterStrategy}
import org.apache.flink.connector.datagen.source.DataGeneratorSource
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.VariableConstants.InputVariableName
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.context.ValidationContext
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
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.{typing, ReturningType}
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.watermarkstrategy.FlinkWatermarkStrategyRuntimeHandler
import pl.touk.nussknacker.engine.flink.watermarkstrategy.FlinkWatermarkStrategyRuntimeHandler.ContextWithEventTime
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.util.watermarkstrategy.{
  WatermarkStrategyOptions,
  WatermarkStrategyValidationHandler,
  WithWatermarkStrategyOptions
}

import java.time.{Duration, Instant}
import java.time.temporal.ChronoUnit
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.annotation.nowarn

object EventGeneratorSourceFactory
    extends SourceFactory
    with SingleInputDynamicComponent
    with UnboundedStreamComponent
    with WatermarkStrategyValidationHandler {

  override type Implementation = FlinkSource

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

  private def standardParametersStep(inputContext: ValidationContext): ContextTransformationDefinition = {
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
      val outputValidationContext = prepareOutputValidationContext(inputContext, valueType)
      NextParameters(
        prepareWatermarkStrategyParameters(outputValidationContext, eventTimeDefaultValueExpression = "#DATE.now".spel)
      )
  }

  override def contextTransformation(inputContext: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition =
    standardParametersStep(inputContext) orElse watermarkStrategyParametersStep(inputContext, dependencies)

  override protected def isIdlenessParameterAvailable: Boolean = false

  override protected def resultAfterWatermarkStrategyParameters(
      inputContext: ValidationContext,
      dependencies: List[NodeDependencyValue]
  )(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(
          (`scheduleParameterName`, _) ::
          (`countParameterName`, _) ::
          (`valueParameterName`, DefinedLazyParameter(valueType)) ::
          _,
          state
        ) =>
      val outputValidationContext = prepareOutputValidationContext(inputContext, valueType)
      FinalResults(outputValidationContext, state = state)
  }

  private def prepareOutputValidationContext(inputContext: ValidationContext, valueType: typing.TypingResult) = {
    inputContext.withVariableUnsafe(InputVariableName, valueType.withoutValue)
  }

  @nowarn("cat=deprecation")
  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[Nothing]
  ): FlinkSource = {
    val schedule                          = scheduleParameterDeclaration.extractValueUnsafe(params)
    val count                             = countParameterDeclaration.extractValue(params).getOrElse(1)
    val value                             = valueParameterDeclaration.extractValueUnsafe(params)
    val extractedWatermarkStrategyOptions = extractWatermarkStrategyOptions(params)

    new FlinkSource
      with ReturningType
      with FlinkSourceTestSupport[AnyRef]
      with TestDataGenerator
      with TestWithParametersSupport[AnyRef]
      with LiveDataProvider
      with WithWatermarkStrategyOptions
      with LazyLogging {

      override val watermarkStrategyOptions: WatermarkStrategyOptions = extractedWatermarkStrategyOptions

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
        env
          .fromSource(
            flinkSource(schedule),
            WatermarkStrategy.noWatermarks(),
            flinkNodeContext.nodeId.id,
          )
          .setUidAndNameToNodeId(flinkNodeContext.nodeId)
          .flatMap(
            (_: Void, out: Collector[Void]) => {
              (1 to localCount).foreach(_ => out.collect(null))
            },
            Types.VOID
          )
          .flatMap(
            new EventGeneratorContextInitializingFunction(
              flinkNodeContext.nodeId,
              flinkNodeContext.convertToEngineRuntimeContext,
              value,
              watermarkStrategyOptions.eventTimeLazyParam,
              flinkNodeContext.lazyParameterHelper
            ),
            FlinkWatermarkStrategyRuntimeHandler.contextInitializingFunctionOutputTypeInfo(
              flinkNodeContext.asOneOutputContext
            )
          )
          .assignTimestampsAndWatermarks(
            FlinkWatermarkStrategyRuntimeHandler.watermarkStrategy(watermarkStrategyOptions)
          )
          .map((ctxWithEventTime: ContextWithEventTime) => ctxWithEventTime.context, flinkNodeContext.contextTypeInfo)
      }

      override val returnType: typing.TypingResult = value.returnType

      override def fetchLiveData(maxNumberOfRecords: Int): DataRecords = {
        val records = List.fill(maxNumberOfRecords)(generateSample())
        DataRecords(records.map { record =>
          DataRecord(
            variables = Map(VariableConstants.InputVariableName -> record),
            // TODO: Respect Event time parameter
            upstreamTimestamp = None
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

      override def watermarkStrategyForTest: Option[WatermarkStrategy[AnyRef]] = None

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

  private def flinkSource(schedule: Duration) = {
    new DataGeneratorSource[Void](
      (_: java.lang.Long) => null,
      Long.MaxValue,
      new RateLimiterStrategy {
        override def createRateLimiter(parallelism: Int): RateLimiter = () =>
          CompletableFuture
            .runAsync(
              () => (),
              CompletableFuture.delayedExecutor(schedule.toMillis, TimeUnit.MILLISECONDS)
            )
      },
      Types.VOID
    )
  }

}

class EventGeneratorContextInitializingFunction(
    nodeId: NodeId,
    convertToEngineRuntimeContext: RuntimeContext => EngineRuntimeContext,
    valueLazyParam: LazyParameter[AnyRef],
    eventTimeLazyParam: LazyParameter[Instant],
    lazyParamHelper: FlinkLazyParameterFunctionHelper
) extends FlinkWatermarkStrategyRuntimeHandler.ContextInitializingFunction[Void](
      nodeId,
      convertToEngineRuntimeContext,
      eventTimeLazyParam,
      lazyParamHelper
    ) {
  private var valueFun: Context => AnyRef = _

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    valueFun = lazyParamHelper
      .createInterpreter(getRuntimeContext)
      .toEvaluateFunction(valueLazyParam)
  }

  override protected def sourceOutputVariables(
      input: Void,
      initialContext: Context
  ): ContextVariables = {
    ContextVariables(
      Map(
        InputVariableName -> valueFun(initialContext)
      )
    )
  }

}
