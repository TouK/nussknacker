package pl.touk.nussknacker.engine.avro.source

import cats.data.Validated.Valid
import org.apache.avro.specific.SpecificRecord
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, NodeDependencyValue}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaRegistryProvider
import pl.touk.nussknacker.engine.avro.{AvroUtils, RuntimeSchemaData}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.kafka.source.KafkaSourceFactory.KafkaSourceImplFactory

import scala.reflect._

/**
 * Source factory for specific records - mainly generated from schema.
 */
class SpecificRecordKafkaAvroSourceFactory[V <: SpecificRecord: ClassTag](schemaRegistryProvider: SchemaRegistryProvider,
                                                                          processObjectDependencies: ProcessObjectDependencies,
                                                                          implProvider: KafkaSourceImplFactory[Any, V])
  extends KafkaAvroSourceFactory[Any, V](schemaRegistryProvider, processObjectDependencies, implProvider) {

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(implicit nodeId: NodeId): NodeTransformationDefinition =
    topicParamStep orElse {
      case step@TransformationStep((`topicParamName`, DefinedEagerParameter(topic:String, _)) :: Nil, _) =>
        val preparedTopic = prepareTopic(topic)

        val clazz = classTag[V].runtimeClass.asInstanceOf[Class[V]]
        val schemaData = RuntimeSchemaData(AvroUtils.extractAvroSpecificSchema(clazz), None)

        prepareSourceFinalResults(preparedTopic, Valid((Some(schemaData), Typed.typedClass(clazz))), context, dependencies, step.parameters, Nil)

      case step@TransformationStep((`topicParamName`, _) :: Nil, _) =>
        prepareSourceFinalErrors(context, dependencies, step.parameters, List.empty)
    }

}
