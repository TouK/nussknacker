package pl.touk.nussknacker.engine.process.scenariotesting

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.Valid
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.livedata.DataRecord
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, ContextVariables}
import pl.touk.nussknacker.engine.api.test.ScenarioTestCommonFormatJsonRecord
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.flink.api.compat.ExplicitUidInOperatorsSupport
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.testmode.CommonTestDataFormatVariablesDecoder
import pl.touk.nussknacker.engine.util.Implicits.RichScalaListMap

import java.util.{Map => JMap}
import scala.jdk.CollectionConverters._

object CommonTestDataFormatStubbedSourcePreparer {

  import NonMapBasedRecordTypesHandler._

  def prepareSubbedSource(
      testRecords: NonEmptyList[(ScenarioTestCommonFormatJsonRecord, Int)],
      sourceOutputValidationContext: ValidationContext
  ): FlinkSource = {
    // We change record type because for now, we can't decode non-Map records from json - see FromJsonTypingResultBasedDecoder for details
    val outputValidationContextWithRecordsAsMaps = sourceOutputValidationContext.mapTypes(_.toMapBasedRecordTypes)

    val decodedRecords = testRecords.map { case (record, testRecordIndex) =>
      val decodedVariables =
        CommonTestDataFormatVariablesDecoder.decode(
          record.variables,
          outputValidationContextWithRecordsAsMaps,
          record.sourceId,
          testRecordIndex
        )
      DataRecord(decodedVariables, record.timestamp)
    }

    CommonTestDataFormatStubbedSource(decodedRecords.toList, outputValidationContextWithRecordsAsMaps)
  }

  private case class CommonTestDataFormatStubbedSource(
      list: List[DataRecord],
      sourceOutputValidationContext: ValidationContext
  ) extends FlinkSource
      with BaseFlinkSource
      with ExplicitUidInOperatorsSupport {

    private val timestampAssigner = new StandardTimestampWatermarkHandler[DataRecord](
      WatermarkStrategy
        .forMonotonousTimestamps[DataRecord]()
        .withTimestampAssigner(
          new SerializableTimestampAssigner[DataRecord] {
            override def extractTimestamp(element: DataRecord, recordTimestamp: Long): Long = {
              // Currently, we allow mixing records with defined timestamp with records without timestamp. It may introduce
              // some unexpected behavior. TODO: We should allow either timestamp defined or not defined for all records in testa data
              lazy val fallbackTimestamp = System.currentTimeMillis()
              element.timestamp.getOrElse(fallbackTimestamp)
            }
          }
        )
    )

    private val contextInitializer: ContextInitializer[DataRecord] =
      new ContextInitializer[DataRecord] {
        override def convertToInitialVariables(record: DataRecord): ContextVariables = {
          ContextVariables(record.variables)
        }

        override def validationContext(
            context: ValidationContext
        )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] =
          Valid(sourceOutputValidationContext)
      }

    override final def contextStream(
        env: StreamExecutionEnvironment,
        flinkNodeContext: FlinkCustomNodeContext
    ): DataStream[Context] = {
      val streamOfRaw = env.fromData(list.asJava, dataRecordTypeInformation)
      // 1. set UID and override source name
      // 2. assign timestamp and watermark policy
      val rawSourceWithUidAndTimestamp = sourceWithUidAndTimestamp(
        streamOfRaw,
        flinkNodeContext,
        Some(timestampAssigner),
      )

      // 3. initialize Context and spool Context to the stream
      rawSourceWithUidAndTimestamp
        .map(
          new FlinkContextInitializingFunction(
            contextInitializer,
            flinkNodeContext.nodeId,
            flinkNodeContext.convertToEngineRuntimeContext
          ),
          flinkNodeContext.contextTypeInfo
        )
    }

    private lazy val dataRecordTypeInformation: TypeInformation[DataRecord] =
      ConcreteCaseClassTypeInfo[DataRecord](
        ("variables", TypeInformationDetection.instance.forVariables(sourceOutputValidationContext.localVariables)),
        ("timestamp", TypeInformation.of(classOf[Option[String]]))
      )

  }

  private object NonMapBasedRecordTypesHandler {

    implicit class TypingResultExt(typingResult: TypingResult) {

      def toMapBasedRecordTypes: TypingResult = typingResult match {
        case union: TypedUnion          => Typed(union.possibleTypes.map(_.toMapBasedRecordTypes))
        case TypedNull                  => TypedNull
        case single: SingleTypingResult => single.toMapBasedRecordTypes
        case unknown: Unknown           => unknown
      }

    }

    private implicit class SingleTypingResultExt(single: SingleTypingResult) {

      def toMapBasedRecordTypes: SingleTypingResult = single match {
        case record @ TypedObjectTypingResult(fields, runtimeObjType, _)
            if runtimeObjType.klass == classOf[JMap[String @unchecked, _]] =>
          record.copy(fields = fields.mapValuesNow(_.toMapBasedRecordTypes))
        case TypedObjectTypingResult(fields, _, _) =>
          Typed.record(fields.mapValuesNow(_.toMapBasedRecordTypes).toList)
        case dict @ TypedDict(_, valueType)                  => dict.copy(valueType = valueType.toMapBasedRecordTypes)
        case tagged @ TypedTaggedValue(underlying, _)        => tagged.copy(underlying.toMapBasedRecordTypes)
        case withValue @ TypedObjectWithValue(underlying, _) => withValue.copy(underlying.toMapBasedRecordTypes)
        case clazz: TypedClass                               => clazz.toMapBasedRecordTypes
      }

    }

    private implicit class TypedClassExt(clazz: TypedClass) {
      def toMapBasedRecordTypes: TypedClass = clazz.copy(params = clazz.params.map(_.toMapBasedRecordTypes))
    }

  }

}
