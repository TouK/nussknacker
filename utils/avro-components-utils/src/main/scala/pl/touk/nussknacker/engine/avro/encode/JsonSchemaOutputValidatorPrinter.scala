package pl.touk.nussknacker.engine.avro.encode

import org.everit.json.schema.{ObjectSchema, Schema, StringSchema}
import pl.touk.nussknacker.engine.json.JsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsMessageFormatter

import java.time.{LocalDate, LocalDateTime, LocalTime}
import scala.collection.JavaConverters

object JsonSchemaOutputValidatorPrinter {

  import OutputValidatorErrorsMessageFormatter._
  import JavaConverters._

  private implicit class ListTypesPrinter(list: List[String]) {
    def printType: String = list.mkString(TypesSeparator)
  }

  def print(schema: Schema): String = schema match {
    case s: ObjectSchema => s.getPropertySchemas.asScala.map {
      case (name, fieldSchema) => s"$name:${print(fieldSchema)}"
    }.mkString("{", ", ", "}")
    case _ => printSchemaType(schema)
  }

  private def printSchemaType(schema: Schema): String = {
    val defaultDisplayType = baseDisplayType(schema) :: Nil
    val logicalTypeDisplayType = printLogicalType(schema)
    (defaultDisplayType ::: logicalTypeDisplayType.toList).printType
  }

  private def baseDisplayType(schema: Schema) = JsonSchemaTypeDefinitionExtractor
    .typeDefinition(schema).display

  //todo: remove duplication - JsonSchemaTypeDefinitionExtractor
  private def printLogicalType(schema: Schema): Option[String] = Option(schema match {
    case s: StringSchema => s.getFormatValidator.formatName() match {
      case "date-time" => classOf[LocalDateTime].getSimpleName
      case "date" => classOf[LocalDate].getSimpleName
      case "time" => classOf[LocalTime].getSimpleName
      case _ => null
    }
    case _ => null
  })

}
