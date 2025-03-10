package pl.touk.nussknacker.engine.flink.util.transformer

import com.github.ghik.silencer.silent
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import io.circe.syntax.EncoderOps
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.util.Collector
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.UnboundedStreamComponent
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.editor.{DualEditor, DualEditorMode, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.{TestData, TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.{typing, ReturningType, TypingResultDecoder}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkSourceTestSupport,
  StandardFlinkSource
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{
  StandardTimestampWatermarkHandler,
  TimestampWatermarkHandler
}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.util.TimestampUtils.supportedTypeToMillis

import java.{util => jul}
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.annotation.Nullable
import javax.validation.constraints.Min
import scala.jdk.CollectionConverters._

// TODO: add testing capabilities
object EventGeneratorSourceFactory
    extends EventGeneratorSourceFactory(
      new StandardTimestampWatermarkHandler[AnyRef](
        WatermarkStrategy
          .forMonotonousTimestamps()
          .withTimestampAssigner(
            new MapAscendingTimestampExtractor(MapAscendingTimestampExtractor.DefaultTimestampField)
          )
      )
    )

class EventGeneratorSourceFactory(customTimestampAssigner: TimestampWatermarkHandler[AnyRef])
    extends SourceFactory
    with UnboundedStreamComponent {

  @silent("deprecated")
  @MethodToInvoke
  def create(
      @ParamName("schedule")
      @DualEditor(
        simpleEditor = new SimpleEditor(
          `type` = SimpleEditorType.DURATION_EDITOR,
          timeRangeComponents = Array(ChronoUnit.DAYS, ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS)
        ),
        defaultMode = DualEditorMode.SIMPLE
      )
      schedule: Duration,
      // TODO: @DefaultValue(1) instead of nullable
      @ParamName("count") @Nullable @Min(1) nullableCount: Integer,
      @ParamName("value") value: LazyParameter[AnyRef]
  ): Source = {
    new StandardFlinkSource[AnyRef]
      with ReturningType
      with FlinkSourceTestSupport[AnyRef]
      with TestDataGenerator
      with TestWithParametersSupport[AnyRef]
      with LazyLogging {

      private val count = Option(nullableCount).map(_.toInt).getOrElse(1)

      override protected def sourceStream(
          env: StreamExecutionEnvironment,
          flinkNodeContext: FlinkCustomNodeContext
      ): DataStream[AnyRef] = {
        // Parameter evaluation requires context, so here we create an empty context just to evaluate the `value` param.
        // Later the evaluated value is extracted from this temporary context and proper context is initialized.
        env
          .addSource(new PeriodicFunction(schedule))
          .flatMap(
            (_: Unit, out: Collector[Context]) => {
              val temporaryContextForEvaluation = Context(flinkNodeContext.metaData.name.value)
              (1 to count).foreach(_ => out.collect(temporaryContextForEvaluation))
            },
            TypeInformationDetection.instance.forClass[Context]
          )
          .flatMap(flinkNodeContext.lazyParameterHelper.lazyMapFunction(value))
          .flatMap(
            (value: ValueWithContext[AnyRef], out: Collector[AnyRef]) => out.collect(value.value),
            TypeInformationDetection.instance.forType[AnyRef](value.returnType)
          )
      }

      override def timestampAssigner: Option[TimestampWatermarkHandler[AnyRef]] = Some(customTimestampAssigner)

      override val returnType: typing.TypingResult = value.returnType

      // Test data generation returns a single batch of samples, as specified in the Event Generator source
      override def generateTestData(size: Int): TestData = TestData(
        List.fill(size)(TestRecord(value.returnType.asJson))
      )

      override def testRecordParser: TestRecordParser[AnyRef] = (testRecords: List[TestRecord]) => {
        testRecords
          .flatMap(_.json.as[TypingResult].toOption.flatMap(_.valueOpt))
          .map(_.asInstanceOf[AnyRef])
      }

      // No parameters are required for ad-hoc tests. Ad-hoc test generates a single batch of samples,
      // with count and value as specified in the Event Generator source
      override def testParametersDefinition: List[Parameter] = List.empty

      override def parametersToTestData(params: Map[ParameterName, AnyRef]): AnyRef =
        value.returnType.valueOpt match {
          case Some(value) =>
            List.fill(count)(value.asInstanceOf[AnyRef])
          case None =>
            throw new IllegalArgumentException(
              s"The value generated by the Event Generator is not specified in the source properties"
            )
        }

      override def timestampAssignerForTest: Option[TimestampWatermarkHandler[AnyRef]] = None

      private implicit val typingResultDecoder: Decoder[TypingResult] = {
        new TypingResultDecoder(name => ClassUtils.forName(name, getClass.getClassLoader)).decodeTypingResults
      }
    }
  }

}

@silent("deprecated")
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

class MapAscendingTimestampExtractor(timestampField: String) extends SerializableTimestampAssigner[AnyRef] {

  override def extractTimestamp(element: scala.AnyRef, recordTimestamp: Long): Long = {
    element match {
      case m: jul.Map[String @unchecked, AnyRef @unchecked] =>
        m.asScala
          .get(timestampField)
          .map(value => supportedTypeToMillis(value, timestampField))
          .getOrElse(System.currentTimeMillis())
      case _ =>
        System.currentTimeMillis()
    }
  }

}

object MapAscendingTimestampExtractor {
  val DefaultTimestampField = "timestamp"
}
