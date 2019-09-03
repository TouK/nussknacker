package pl.touk.nussknacker.engine.avro.typed

import java.nio.ByteBuffer

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericData.EnumSymbol
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}

object AvroSchemaTypeDefinitionExtractor {

  import collection.JavaConverters._

  def typeDefinition(schemaString: String): TypingResult =
    typeDefinition(new Schema.Parser().parse(schemaString))

  // see BestEffortAvroEncoder for underlying avro types
  def typeDefinition(schema: Schema): TypingResult = {
    schema.getType match {
      case Schema.Type.RECORD =>
        TypedObjectTypingResult(
          schema.getFields.asScala.map { field =>
            field.name() -> typeDefinition(field.schema())
          }.toMap
        )
      case Schema.Type.ENUM =>
        Typed[EnumSymbol]
      case Schema.Type.ARRAY =>
        TypedClass(classOf[java.util.List[_]], List(typeDefinition(schema.getElementType)))
      case Schema.Type.MAP =>
        TypedClass(classOf[java.util.Map[_, _]], List(Typed[CharSequence], typeDefinition(schema.getValueType)))
      case Schema.Type.UNION =>
        val childTypeDefinitons = schema.getTypes.asScala.map(typeDefinition).toList
        val withoutNull = childTypeDefinitons.filterNot(_ == Typed[Null])
        withoutNull match {
          case Nil => Typed[Null]
          case nonEmpty =>
            Typed(nonEmpty.toSet)
        }
      case Schema.Type.FIXED =>
        Typed[GenericData.Fixed]
      case Schema.Type.STRING =>
        Typed[CharSequence]
      case Schema.Type.BYTES =>
        Typed[ByteBuffer]
      case Schema.Type.INT =>
        Typed[Integer]
      case Schema.Type.LONG =>
        Typed[Long]
      case Schema.Type.FLOAT =>
        Typed[Float]
      case Schema.Type.DOUBLE =>
        Typed[Double]
      case Schema.Type.BOOLEAN =>
        Typed[Boolean]
      case Schema.Type.NULL =>
        Typed[Null]
    }
  }

}
