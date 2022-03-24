package pl.touk.nussknacker.engine.json

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.definition.TypedNodeDependency

import scala.util.{Failure, Success, Try}

class JsonSchemaExtractor {

  def getSchemaFromProperty(property:String, dependencies: List[NodeDependencyValue]): Validated[NonEmptyList[ProcessCompilationError], Schema] = {
    val metaData = TypedNodeDependency[MetaData].extract(dependencies)
    val nodeId = TypedNodeDependency[NodeId].extract(dependencies)

    def invalid(message: String): Invalid[NonEmptyList[CustomNodeError]] = Invalid(NonEmptyList.one(CustomNodeError(message, None)(nodeId)))

    metaData.additionalFields.flatMap(_.properties.get(property))
      .map(rawSchema => Try(JsonSchemaBuilder.parseSchema(rawSchema)) match {
        case Success(schema) => Valid(schema)
        case Failure(exc) => invalid(s"""Error at parsing \"$property\": ${exc.getMessage}.""")
      })
      .getOrElse(invalid(s"""Missing \"$property\" property."""))
  }

}
