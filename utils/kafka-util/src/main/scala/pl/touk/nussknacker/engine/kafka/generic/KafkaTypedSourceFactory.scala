package pl.touk.nussknacker.engine.kafka.generic

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.definition.{JsonParameterEditor, MandatoryParameterValidator, NotBlankParameterValidator, Parameter}
import pl.touk.nussknacker.engine.api.typed.{TypedMap, typing}
import pl.touk.nussknacker.engine.util.typing.TypingUtils

import scala.util.{Failure, Success, Try}

object KafkaTypedSourceFactory {
  type CalculateType = Validated[InvalidTypeDefinition, (java.util.Map[String, _], typing.TypingResult)]
  type TypeDefinition = java.util.Map[_, _]
  type TypedJson = ConsumerRecord[String, TypedMap]

  final val TypeDefinitionParamName = "type"

  final val TypeParameter = Parameter[java.util.Map[String, _]](TypeDefinitionParamName).copy(
    validators = List(MandatoryParameterValidator, NotBlankParameterValidator),
    editor = Some(JsonParameterEditor)
  )

  def calculateTypingResult(value: Any)(implicit nodeId: NodeId): CalculateType = {
    Try({
      val definition = value.asInstanceOf[java.util.Map[String, _]]
      (definition, TypingUtils.typeMapDefinition(definition))
    }) match {
      case Success((definition, typingResult)) => Valid(definition, typingResult)
      case Failure(exc) => Invalid(InvalidTypeDefinition(s"Can't resolve fields type from $value. Error message: $exc."))
    }
  }

  case class InvalidTypeDefinition(message: String) extends RuntimeException {
    def toCustomNodeError(nodeId: NodeId): CustomNodeError =
      new CustomNodeError(nodeId.id, message, Some(TypeDefinitionParamName))
  }
}
