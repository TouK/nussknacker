package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.jsonschemautils

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.SinkFactory

import scala.util.{Failure, Success, Try}

trait JsonRequestResponseBaseTransformer[T] extends SingleInputGenericNodeTransformation[T] with SinkFactory {

  protected val metaDataDependency: TypedNodeDependency[MetaData] = TypedNodeDependency[MetaData]

  protected val nodeIdDependency: TypedNodeDependency[NodeId] = TypedNodeDependency[NodeId]

  override def nodeDependencies: List[NodeDependency] = List(metaDataDependency, nodeIdDependency)

  protected def prepareMetadata(dependencies: List[NodeDependencyValue]): MetaData =
    metaDataDependency.extract(dependencies)

  protected def prepareNodeId(dependencies: List[NodeDependencyValue]): NodeId =
    nodeIdDependency.extract(dependencies)

  protected def getSchemaFromProperty(property:String, dependencies: List[NodeDependencyValue]): Validated[NonEmptyList[ProcessCompilationError], Schema] = {
    val metaData = prepareMetadata(dependencies)
    val nodeId = prepareNodeId(dependencies)

    def invalid(message: String) = Invalid(NonEmptyList.one(CustomNodeError(message, None)(nodeId)))

    metaData.additionalFields.flatMap(_.properties.get(property))
      .map(rawSchema => Try(JsonSchemaUtil.parseSchema(rawSchema)) match {
        case Success(schema) => Valid(schema)
        case Failure(exc) => invalid(s"""Error at parsing \"$property\": ${exc.getMessage}.""")
      })
      .getOrElse(invalid(s"""Missing \"$property\" property."""))
  }

}
