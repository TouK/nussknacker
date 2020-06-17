package pl.touk.nussknacker.engine.avro.typed

import java.nio.ByteBuffer

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData.EnumSymbol
import org.apache.avro.generic.{GenericData, GenericRecord}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}

/**
  * Right now we're doing approximate type generation to avoid false positives in validation,
  * so now we add option to skip nullable fields.
  *
  * @TODO In future should do it in another way
  *
  * @param skippNullableFields
  */
class AvroSchemaTypeDefinitionExtractor(skippNullableFields: Boolean) {

  import collection.JavaConverters._

  // see BestEffortAvroEncoder for underlying avro types
  def typeDefinition(schema: Schema): TypingResult = {
    schema.getType match {
      case Schema.Type.RECORD => {
        val fields = schema.getFields.asScala.filterNot(field => skippNullableFields && field.schema().isNullable)
        TypedObjectTypingResult(
          fields.map { field =>
            field.name() -> typeDefinition(field.schema())
          }.toMap,
          Typed.typedClass[GenericRecord]
        )
      }
      case Schema.Type.ENUM =>
        Typed[EnumSymbol]
      case Schema.Type.ARRAY =>
        Typed.genericTypeClass[java.util.List[_]](List(typeDefinition(schema.getElementType)))
      case Schema.Type.MAP =>
        Typed.genericTypeClass[java.util.Map[_, _]](List(Typed[CharSequence], typeDefinition(schema.getValueType)))
      case Schema.Type.UNION =>
        val childTypeDefinitons = schema.getTypes.asScala.map(typeDefinition).toSet
        Typed(childTypeDefinitons)
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
        Typed.empty
    }
  }
}

object AvroSchemaTypeDefinitionExtractor {

  private lazy val withoutOptionallyFieldsExtractor = new AvroSchemaTypeDefinitionExtractor(skippNullableFields = true)

  private lazy val withOptionallyFieldsExtractor = new AvroSchemaTypeDefinitionExtractor(skippNullableFields = false)

  def typeDefinitionWithoutNullableFields(schema: Schema): TypingResult =
    withoutOptionallyFieldsExtractor.typeDefinition(schema)

  def typeDefinition(schema: Schema): TypingResult =
      withOptionallyFieldsExtractor.typeDefinition(schema)
}
