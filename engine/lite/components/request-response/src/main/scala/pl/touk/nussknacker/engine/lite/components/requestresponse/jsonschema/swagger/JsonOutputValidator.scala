package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.swagger

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sinks.json.JsonRequestResponseSinkFactory.SinkValueParamName
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

object JsonOutputValidator extends LazyLogging {

  private val ValidationErrorMessageBase = "Provided value does not match scenario output JSON schema"

  def validateOutput(value: TypingResult, schema: Schema)(implicit nodeId: NodeId): Validated[CustomNodeError, Unit] = {
    new JsonSchemaSubclassDeterminer(schema)
      .validateTypingResultToSchema(value)
      .leftMap(errors => prepareError(errors.toList))
  }

  private def prepareError(errors: List[String])(implicit nodeId: NodeId) = errors match {
    case Nil => CustomNodeError(ValidationErrorMessageBase, Option(SinkValueParamName))
    case _ => CustomNodeError(errors.mkString(s"$ValidationErrorMessageBase - errors:\n", ", ", ""), Option(SinkValueParamName))
  }

}