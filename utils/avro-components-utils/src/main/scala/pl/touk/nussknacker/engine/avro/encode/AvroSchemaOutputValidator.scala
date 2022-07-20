package pl.touk.nussknacker.engine.avro.encode

import cats.data.Validated.{Invalid, condNel}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.Schema.Type
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType
import org.apache.avro.generic.GenericData.{EnumSymbol, Fixed}
import org.apache.avro.{LogicalTypes, Schema, SchemaCompatibility}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.avro.AvroUtils
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.output._

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.util.Try

private[encode] case class AvroSchemaExpected(schema: Schema) extends OutputValidatorExpected {
  override def expected: String = AvroSchemaOutputValidatorPrinter.print(schema)
}

object AvroSchemaOutputValidator {
  private[encode] val SimpleAvroPath = "Data"
}

class AvroSchemaOutputValidator(validationMode: ValidationMode) extends LazyLogging {

  import AvroSchemaOutputValidator._

  import scala.collection.JavaConverters._

  private val valid = Validated.Valid(())

  private val longLogicalTypes = Set(
    LogicalTypes.timeMicros(), LogicalTypes.timestampMillis(), LogicalTypes.timestampMicros()
  )

  /**
    * see {@link pl.touk.nussknacker.engine.avro.encode.BestEffortAvroEncoder} for underlying avro types
    */
  def validateTypingResultToSchema(typingResult: TypingResult, parentSchema: Schema)(implicit nodeId: NodeId): ValidatedNel[OutputValidatorError, Unit] =
    validateTypingResult(typingResult, parentSchema, None)

  final private def validateTypingResult(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
    (typingResult, schema.getType) match {
      case (tc@TypedClass(cl, _), _) if AvroUtils.isSpecificRecord(cl) =>
        validateSpecificRecord(tc, schema, path)
      case (record: TypedObjectTypingResult, Type.RECORD) =>
        validateRecordSchema(record, schema, path)
      case (map: TypedObjectTypingResult, Type.MAP) =>
        validateMapSchema(map, schema, path)
      case (tc@TypedClass(map, _), Type.MAP) if classOf[java.util.Map[_, _]].isAssignableFrom(map)  =>
        validateTypedClassToMapSchema(tc, schema, path)
      case (array@TypedClass(cl, _), Type.ARRAY) if classOf[java.util.List[_]].isAssignableFrom(cl) =>
        validateArraySchema(array, schema, path)
      case (_@TypedNull, _) if !schema.isNullable =>
        invalid(typingResult, schema, path)
      case (_@TypedNull, _) if schema.isNullable =>
        valid
      case (typingResult, Type.ENUM) =>
        validateEnum(typingResult, schema, path)
      case (typingResult, Type.FIXED) =>
        validateFixed(typingResult, schema, path)
      case (_, Type.STRING) if schema.getLogicalType == LogicalTypes.uuid() =>
        validateUUID(typingResult, schema, path)
      case (_, Type.INT) if schema.getLogicalType == LogicalTypes.date() =>
        validateClass[java.lang.Integer](typingResult, schema, path)
      case (_, Type.INT) if schema.getLogicalType == LogicalTypes.timeMillis() =>
        validateClass[java.lang.Integer](typingResult, schema, path)
      case (_, Type.LONG) if longLogicalTypes.contains(schema.getLogicalType) =>
        validateClass[java.lang.Long](typingResult, schema, path)
      case (anyTypingResult, Type.UNION) =>
        val results = validateUnionSchema(anyTypingResult, schema, path)
        results
      case (_, _) if AvroUtils.isLogicalType[LogicalTypes.Decimal](schema) =>
        validateClass[ByteBuffer](typingResult, schema, path)
      case (_, _) =>
        canBeSubclassOf(typingResult, schema, path)
    }
  }

  private def validateSpecificRecord(tc: TypedClass, schema: Schema, path: Option[String]) = {
    val valueSchema = AvroUtils.extractAvroSpecificSchema(tc.klass)
    // checkReaderWriterCompatibility is more accurate than our validation with given ValidationMode
    val compatibility = SchemaCompatibility.checkReaderWriterCompatibility(schema, valueSchema)
    if (compatibility.getType == SchemaCompatibilityType.COMPATIBLE) {
      valid
    } else {
      val typingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(valueSchema)
      validateTypingResult(typingResult, schema, path)
    }
  }

  private def validateRecordSchema(record: TypedObjectTypingResult, schema: Schema, path: Option[String]): Validated[NonEmptyList[OutputValidatorError], Unit] = {
    val schemaFields = schema.getFields.asScala.map(field => field.name() -> field).toMap
    val requiredFieldNames = schemaFields.values.filterNot(_.hasDefaultValue).map(_.name())
    val fieldsToValidate: Map[String, TypingResult] = record.fields.filterKeys(schemaFields.contains)

    def prepareFields(fields: Set[String]) = fields.flatMap(buildPath(_, path))

    val requiredFieldsValidation = {
      val missingFields = requiredFieldNames.filterNot(record.fields.contains).toList.sorted.toSet
      condNel(missingFields.isEmpty, (), OutputValidatorMissingFieldsError(prepareFields(missingFields)))
    }

    val schemaFieldsValidation = {
      fieldsToValidate.flatMap{ case (key, value) =>
        val fieldPath = buildPath(key, path)
        schemaFields.get(key).map(f => validateTypingResult(value, f.schema(), fieldPath))
      }.foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)
    }

    val redundantFieldsValidation = {
      val redundantFields = record.fields.keySet.diff(schemaFields.keySet)
      condNel(validationMode.acceptRedundant || redundantFields.isEmpty, (), OutputValidatorRedundantFieldsError(prepareFields(redundantFields)))
    }

   requiredFieldsValidation combine schemaFieldsValidation combine redundantFieldsValidation
  }

  private def validateTypedClassToMapSchema(map: TypedClass, schema: Schema, path: Option[String]) = {
    map.params match {
      case _ :: value :: Nil =>
        validateTypingResult(value, schema.getValueType, buildPath("*", path, isGeneric = true))
      case _ => //TODO: will be there more then two parameters?
        canBeSubclassOf(map, schema, path)
    }
  }

  private def validateMapSchema(map: TypedObjectTypingResult, schema: Schema, path: Option[String]) = {
    val schemaFieldsValidationResult = map.fields.map{ case (key, value) =>
      val fieldPath = buildPath(key, path)
      validateTypingResult(value, schema.getValueType, fieldPath)
    }.foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)

    schemaFieldsValidationResult
  }

  private def validateArraySchema(array: TypedClass, schema: Schema, path: Option[String]) = {
     val valuesValidationResult = array
        .params
        .zipWithIndex
        .map { case (el, index) =>
          val pathKey = array.params match {
            case _ :: Nil => "" //One element list is 'general' object, and we preset it as field[]
            case _ => index.toString
          }

          val elementPath = buildPath(pathKey, path, isGeneric = true)
          validateTypingResult(el, schema.getElementType, elementPath)
        }
        .foldLeft[ValidatedNel[OutputValidatorError, Unit]](().validNel)((a, b) => a combine b)

    valuesValidationResult
  }

  private def validateUnionSchema(union: TypingResult, schema: Schema, path: Option[String]) = {
    implicit class UnionValidationResult(results: List[ValidatedNel[OutputValidatorError, Unit]]) {
      type ValidatedType = ValidatedNel[OutputValidatorError, Unit]
      def toValidated: ValidatedNel[OutputValidatorError, Unit] =
        if (results.exists(_.isValid)) valid else results.foldLeft[ValidatedType](().validNel)((a, b) => a combine b)
    }

    object UnionNullableSchemaType {
      private val MaxSizeUnion = 2
      def unapply(schema: Schema): Option[Schema] = {
        Option(schema).filter(_.getTypes.size() == MaxSizeUnion).filter(_.isNullable).flatMap(_.getTypes.asScala.find(!_.isNullable))
      }
    }

    //check is there only one typing error with exactly same field as path - it means there was checking whole object (without going deeper e.g. List/Map/Record)
    def singleObjectTypingError(errors: NonEmptyList[OutputValidatorError]): Boolean =
      errors.collect{case err: OutputValidatorTypeError => err} match {
        case head :: Nil => path.exists(_.equals(head.field))
        case _ => false
      }

    def createUnionValidationResults(withoutNullability: Boolean): List[ValidatedNel[OutputValidatorError, Unit]] = {
      val schemas = if(withoutNullability) schema.getTypes.asScala.filterNot(_.isNullable) else schema.getTypes.asScala
      schemas.map(validateTypingResult(union, _, path)).toList
    }

    val results = schema match {
      case UnionNullableSchemaType(_) => //Nullability: union[null, any] it's most common situation, so we want to handle that in special way
        val notNullableResults = createUnionValidationResults(withoutNullability = true)
        notNullableResults.toValidated match {
          case Invalid(errors) if singleObjectTypingError(errors) => //when single typing error is true, we have to validate again including nullability
            createUnionValidationResults(withoutNullability = false)
          case _ =>
            notNullableResults
        }
      case _ =>
        createUnionValidationResults(withoutNullability = false)
    }

    results.toValidated
  }

  private def validateEnum(typingResult: TypingResult, schema: Schema, path: Option[String]): Validated[NonEmptyList[OutputValidatorError], Unit] = {
    def isValueValid(typedWithObject: TypedObjectWithValue, schema: Schema): Boolean = {
      val enumValue = typedWithObject.value match {
        case enum: EnumSymbol => Some(`enum`.toString)
        case str: String => Some(str)
        case _ => None
      }

      enumValue.exists(schema.getEnumSymbols.asScala.contains)
    }

    validateWithValue[java.lang.String](isValueValid, typingResult: TypingResult, schema: Schema, path: Option[String])
  }

  private def validateFixed(typingResult: TypingResult, schema: Schema, path: Option[String]): Validated[NonEmptyList[OutputValidatorError], Unit] = {
    def isValueValid(typedWithObject: TypedObjectWithValue, schema: Schema): Boolean = {
      val fixedStringValue = typedWithObject.value match {
        case fixed: Fixed => Some(fixed.bytes())
        case buffer: ByteBuffer => Some(buffer.array())
        case bytes: Array[Byte] => Some(bytes)
        case str: String => Some(str.getBytes(StandardCharsets.UTF_8))
        case _ => None
      }

      fixedStringValue.exists(_.length == schema.getFixedSize)
    }

    validateWithValue[java.lang.String](isValueValid, typingResult: TypingResult, schema: Schema, path: Option[String])
  }

  private def validateUUID(typingResult: TypingResult, schema: Schema, path: Option[String]): Validated[NonEmptyList[OutputValidatorError], Unit] = {
    def isValueValid(typedWithObject: TypedObjectWithValue, schema: Schema): Boolean = {
      typedWithObject.value match {
        case _:UUID => true
        case str: String => Try(UUID.fromString(str)).toValidated.isValid
        case _ => false
      }
    }

    validateWithValue[java.lang.String](isValueValid, typingResult: TypingResult, schema: Schema, path: Option[String])
  }

  private def validateWithValue[T:ClassTag](isValueValid: (TypedObjectWithValue, Schema) => Boolean, typingResult: TypingResult, schema: Schema, path: Option[String]) = {
    val typeResult = validateClass[T](typingResult, schema, path)

    typingResult match {
      case obj: TypedObjectWithValue if typeResult.isValid && isValueValid(obj, schema) => valid
      case _: TypedObjectWithValue => invalid(typingResult, schema, path)
      case _ if typeResult.isValid => valid
      case _ => typeResult
    }
  }

  private def validateClass[T:ClassTag](typingResult: TypingResult, schema: Schema, path: Option[String]): Validated[NonEmptyList[OutputValidatorTypeError], Unit] = {
    val schemaAsTypedResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
    val clazz: Class[_] = implicitly[ClassTag[T]].runtimeClass

    (schemaAsTypedResult, typingResult) match {
      case (TypedClass(schemaClass, _), TypedClass(typeClass, _)) if typeClass.equals(schemaClass) => valid
      case (_, TypedClass(typeClass, _)) if clazz.equals(typeClass) => valid
      case (_, TypedObjectWithValue(underlying, _)) if underlying.klass.equals(clazz) => valid
      case _ => invalid(typingResult, schema, path) //we don't use canBeSubclassOf here, because it can return true eg. Integer (Number) vs BigDecimal but Avro doesn't allow for that...
    }
  }

  /**
    * TODO: Consider verification class instead of using .canBeSubclassOf from Typing - at avro we want to avoid:
    * * Unknown.canBeSubclassOf(Any) => true
    * * Long.canBeSubclassOf(Integer) => true
    * Should we use strict verification at avro?
    */
  private def canBeSubclassOf(objTypingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorError, Unit] = {
      val schemaAsTypedResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
      condNel(objTypingResult.canBeSubclassOf(schemaAsTypedResult), (),
        OutputValidatorTypeError(path.getOrElse(SimpleAvroPath), objTypingResult, AvroSchemaExpected(schema))
      )
  }

  private def invalid(typingResult: TypingResult, schema: Schema, path: Option[String]): ValidatedNel[OutputValidatorTypeError, Nothing] =
    Validated.invalidNel(OutputValidatorTypeError(path.getOrElse(SimpleAvroPath), typingResult, AvroSchemaExpected(schema)))

  private def buildPath(key: String, path: Option[String], isGeneric: Boolean = false) = Some(
    path.map(p => if(isGeneric) s"$p[$key]" else s"$p.$key").getOrElse(key)
  )

}
