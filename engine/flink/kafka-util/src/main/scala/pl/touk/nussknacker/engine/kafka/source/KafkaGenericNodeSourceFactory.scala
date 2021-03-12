package pl.touk.nussknacker.engine.kafka.source

import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import org.apache.flink.types.Nothing
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, OutputVariableNameValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, NodeDependency, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.flink.api.process.FlinkSource
import pl.touk.nussknacker.engine.flink.util.context.{InitContextFunction, VariableProvider}
import pl.touk.nussknacker.engine.kafka.{KafkaConfig, KafkaUtils, RecordFormatter}
import pl.touk.nussknacker.engine.kafka.serialization.KafkaDeserializationSchemaFactory

import scala.reflect.ClassTag


class KafkaGenericNodeSourceFactory[T: ClassTag](deserializationSchemaFactory: KafkaDeserializationSchemaFactory[T],
                                                 timestampAssigner: Option[TimestampWatermarkHandler[T]],
                                                 formatter: RecordFormatter,
                                                 processObjectDependencies: ProcessObjectDependencies,
                                                 variableProvider: Option[VariableProvider[T]])
  extends BaseKafkaSourceFactory(deserializationSchemaFactory, timestampAssigner, formatter, processObjectDependencies)
    with SingleInputGenericNodeTransformation[FlinkSource[T]] {

  override type State = Nothing

  override def initialParameters: List[Parameter] = Parameter[String](KafkaGenericNodeSourceFactory.TopicParamName)
    .copy(editor = Some(DualParameterEditor(
      simpleEditor = StringParameterEditor,
      defaultMode = DualEditorMode.RAW))
    )  :: Nil

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: ProcessCompilationError.NodeId)
  : NodeTransformationDefinition = {
    case TransformationStep(Nil, _) => NextParameters(initialParameters)
    case TransformationStep((KafkaGenericNodeSourceFactory.TopicParamName, _)::Nil, None) => FinalResults(finalCtx(context, dependencies))
  }

  private def finalCtx(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): ValidationContext = {
    val name = dependencies.collectFirst {
      case OutputVariableNameValue(name) => name
    }.get

    variableProvider
      .map(_.validationContext(context, name))
      .getOrElse(context.withVariable(OutputVar.customNode(name), Typed[T]).getOrElse(context))
  }

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[Nothing]): FlinkSource[T] = {
    val topic = params(KafkaGenericNodeSourceFactory.TopicParamName).asInstanceOf[String]
    val kafkaConfig = KafkaConfig.parseProcessObjectDependencies(processObjectDependencies)
    createSource(List(topic), kafkaConfig)
  }

  override protected def createSource(topics: List[String], kafkaConfig: KafkaConfig): KafkaSource[T] = {
    val preparedTopics = topics.map(KafkaUtils.prepareKafkaTopic(_, processObjectDependencies))
    val deserializationSchema = deserializationSchemaFactory.create(topics, kafkaConfig)
    new KafkaSource(preparedTopics, kafkaConfig, deserializationSchema, timestampAssigner, formatter) {
      override def initContext(processId: String, taskName: String): InitContextFunction[T] =
        variableProvider.map(_.initContext(processId, taskName)).getOrElse(super.initContext(processId, taskName))
    }
  }

  override def nodeDependencies: List[NodeDependency] = Nil
}

object KafkaGenericNodeSourceFactory {
  final val TopicParamName = "Topic"
}
