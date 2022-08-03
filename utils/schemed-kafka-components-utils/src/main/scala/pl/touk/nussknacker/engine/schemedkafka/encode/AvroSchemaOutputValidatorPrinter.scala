package pl.touk.nussknacker.engine.schemedkafka.encode

import org.apache.avro.{LogicalType, LogicalTypes, Schema}
import pl.touk.nussknacker.engine.schemedkafka.schema.AvroStringSettings
import pl.touk.nussknacker.engine.schemedkafka.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.output.{OutputValidatorErrorsConverter, OutputValidatorErrorsMessageFormatter}

import scala.collection.JavaConverters

object AvroSchemaOutputValidatorPrinter {

  import OutputValidatorErrorsMessageFormatter._
  import cats._
  import implicits._
  import JavaConverters._

  private implicit class ListTypesPrinter(list: List[String]) {
    def printType: String = list.mkString(TypesSeparator)
  }

  private def logicalTypesClassMapping: Map[Class[_], List[Class[_]]] = Map(
    classOf[LogicalTypes.Decimal] -> List(classOf[java.nio.ByteBuffer]),
    classOf[LogicalTypes.Date] -> List(classOf[java.lang.Integer]),
    classOf[LogicalTypes.TimeMillis] -> List(classOf[java.lang.Integer]),
    classOf[LogicalTypes.TimeMicros] -> List(classOf[java.lang.Long]),
    classOf[LogicalTypes.TimestampMillis] -> List(classOf[java.lang.Long]),
    classOf[LogicalTypes.TimestampMicros] -> List(classOf[java.lang.Long]),
    classOf[LogicalTypes] -> List(classOf[java.lang.Long]),
  )

  private def logicalTypesMapping: Map[LogicalType, List[Class[_]]] = Map(
    LogicalTypes.uuid() -> List(classOf[java.lang.String]),
  )

  private def schemaTypeMapping = Map(
    Schema.Type.FIXED -> List(classOf[java.nio.ByteBuffer], AvroStringSettings.stringTypingResult.klass),
    Schema.Type.ENUM -> List(AvroStringSettings.stringTypingResult.klass),
  )

  def print(schema: Schema): String = {
    schema.getType match {
      case Schema.Type.RECORD =>
        schema
          .getFields
          .asScala
          .map(f => s"${f.name()}: ${print(f.schema())}")
          .mkString("{", ", ", "}")
      case Schema.Type.ARRAY =>
        s"List[${print(schema.getElementType)}]"
      case Schema.Type.MAP =>
        s"Map[String, ${print(schema.getValueType)}]"
      case Schema.Type.UNION =>
        schema.getTypes.asScala.map(print).toList.printType
      case _ =>
        printSchemaType(schema)
    }
  }

  private def printSchemaType(schema: Schema): String = {
    val defaultDisplayType = baseDisplayType(schema) :: Nil
    val typeDisplayType = schemaTypeMapping.getOrElse(schema.getType, Nil).map(_.getSimpleName)
    val logicalTypeDisplayType = printLogicalType(schema)
    (defaultDisplayType ::: logicalTypeDisplayType ::: typeDisplayType).printType
  }

  private def baseDisplayType(schema: Schema) = {
    val typed = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
    schema.getType match {
      case Schema.Type.FIXED => s"${typed.display}[${schema.getFixedSize}]"
      case Schema.Type.ENUM => s"${typed.display}[${schema.getEnumSymbols.asScala.toList.printType}]"
      case _ => typed.display
    }
  }

  private def printLogicalType(schema: Schema): List[String] =
    Option(schema.getLogicalType)
      .flatMap(lt => logicalTypesMapping.get(lt) |+| logicalTypesClassMapping.get(lt.getClass))
      .map(classes => classes.map(_.getSimpleName))
      .getOrElse(Nil)

}
