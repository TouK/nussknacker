package pl.touk.nussknacker.engine.kafka.generic

import cats.data.Validated.{Invalid, Valid}
import io.circe.Decoder
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, ProcessObjectDependencies, Source}
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.source.EspDeserializationSchema
import pl.touk.nussknacker.engine.kafka.KafkaFactory.TopicParamName
import pl.touk.nussknacker.engine.kafka._
import pl.touk.nussknacker.engine.kafka.consumerrecord.FixedValueDeserializationSchemaFactory
import pl.touk.nussknacker.engine.kafka.generic.KafkaDelayedSourceFactory._
import pl.touk.nussknacker.engine.kafka.generic.KafkaTypedSourceFactory._
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchema
import pl.touk.nussknacker.engine.kafka.serialization.schemas._
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSourceFactory

//TODO: Move it to source package
object sources {

  import collection.JavaConverters._

  class GenericJsonSourceFactory(processObjectDependencies: ProcessObjectDependencies) extends FlinkKafkaSourceFactory[String, java.util.Map[_, _]](
    new FixedValueDeserializationSchemaFactory(JsonMapDeserialization), None, jsonFormatterFactory, processObjectDependencies)

  class GenericTypedJsonSourceFactory(processObjectDependencies: ProcessObjectDependencies) extends FlinkKafkaSourceFactory[String, TypedMap](
    new FixedValueDeserializationSchemaFactory(JsonTypedMapDeserialization), None, jsonFormatterFactory, processObjectDependencies) with BaseGenericTypedJsonSourceFactory

  class DelayedGenericTypedJsonSourceFactory(formatterFactory: RecordFormatterFactory,
                                             processObjectDependencies: ProcessObjectDependencies,
                                             timestampAssigner: Option[TimestampWatermarkHandler[TypedJson]])
    extends FlinkKafkaSourceFactory[String, TypedMap](
      new FixedValueDeserializationSchemaFactory(JsonTypedMapDeserialization),
      timestampAssigner,
      formatterFactory,
      processObjectDependencies
    ) with BaseKafkaDelayedSourceFactory {

    override protected def prepareInitialParameters: List[Parameter] = super.prepareInitialParameters ++ List(
      TypeParameter, TimestampFieldParameter, DelayParameter
    )

    override def nextSteps(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition = {
      case step@TransformationStep(
      (TopicParamName, DefinedEagerParameter(topic: String, _)) ::
        (TypeDefinitionParamName, DefinedEagerParameter(definition: TypeDefinition, _)) ::
        (TimestampFieldParamName, DefinedEagerParameter(field, _)) ::
        (DelayParameterName, DefinedEagerParameter(delay, _)) :: Nil, _
      ) =>
        val topicValidationErrors = topicsValidationErrors(topic)
        calculateTypingResult(definition) match {
          case Valid((definition, typingResult)) =>
            val delayValidationErrors = Option(delay.asInstanceOf[java.lang.Long]).map(d => validateDelay(d)).getOrElse(Nil)
            val timestampValidationErrors = Option(field.asInstanceOf[String]).map(f => validateTimestampField(f, typingResult)).getOrElse(Nil)
            val errors = topicValidationErrors ++ timestampValidationErrors ++ delayValidationErrors
            prepareSourceFinalResults(context, dependencies, step.parameters, keyTypingResult, typingResult, errors)
          case Invalid(exc) =>
            val errors = topicValidationErrors ++ List(exc.toCustomNodeError(nodeId))
            prepareSourceFinalErrors(context, dependencies, step.parameters, errors = errors)
        }
      case step@TransformationStep((TopicParamName, _) :: (TypeDefinitionParamName, _) :: (TimestampFieldParamName, _) :: (DelayParameterName, _) :: Nil, _) =>
        prepareSourceFinalErrors(context, dependencies, step.parameters, errors = Nil)
    }

    override protected def createSource(params: Map[String, Any],
                                        dependencies: List[NodeDependencyValue],
                                        finalState: Option[State],
                                        preparedTopics: List[PreparedKafkaTopic],
                                        kafkaConfig: KafkaConfig,
                                        deserializationSchema: KafkaDeserializationSchema[TypedJson],
                                        formatter: RecordFormatter,
                                        contextInitializer: ContextInitializer[TypedJson]): Source[TypedJson] = {
      extractDelayInMillis(params) match {
        case millis if millis > 0 =>
          val timestampFieldName = extractTimestampField(params)
          val timestampAssignerWithExtract: Option[TimestampWatermarkHandler[TypedJson]] =
            Option(timestampFieldName)
              .map(fieldName => {
                prepareTimestampAssigner(
                  kafkaConfig,
                  extractTimestampFromField(fieldName)
                )
              }).orElse(timestampAssigner)
          createDelayedKafkaSourceWithFixedDelay[String, TypedMap](preparedTopics, kafkaConfig, deserializationSchema, timestampAssignerWithExtract, formatter, contextInitializer, millis)
        case _ =>
          super.createSource(params, dependencies, finalState, preparedTopics, kafkaConfig, deserializationSchema, formatter, contextInitializer)
      }
    }

    def extractTimestampFromField(fieldName: String): SerializableTimestampAssigner[TypedJson] = new SerializableTimestampAssigner[TypedJson] {

      override def extractTimestamp(element: TypedJson, recordTimestamp: Long): Long = {
        // TODO: Handle exceptions thrown within sources (now the whole process fails)
        Option(element.value().get(fieldName))
          .map(_.asInstanceOf[Long])
          .getOrElse(0L) // explicit null to 0L conversion (instead of implicit unboxing)
      }
    }
  }

  object JsonMapDeserialization extends EspDeserializationSchema[java.util.Map[_, _]](deserializeToMap)

  object JsonTypedMapDeserialization extends EspDeserializationSchema[TypedMap](deserializeToTypedMap)

  //TOOD: better error handling?
  class JsonDecoderDeserialization[T:Decoder:TypeInformation] extends EspDeserializationSchema[T](CirceUtil.decodeJsonUnsafe[T](_))

}
