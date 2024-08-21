package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.table.types.logical._
import org.apache.flink.types.Row
import org.springframework.util.NumberUtils
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.flink.api.TypedMultiset
import pl.touk.nussknacker.engine.flink.table.utils.DataTypesExtensions._

import java.util
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

object BestEffortTableTypeSchemaEncoder {

  private val javaMapClass = classOf[java.util.Map[_, _]]

  private val rowClass = classOf[Row]

  private val listClass = classOf[java.util.List[_]]

  private val arrayClass = classOf[Array[AnyRef]]

  def encode(value: Any, targetType: LogicalType): Any = {
    val alignedValue = (value, targetType) match {
      case (null, _) =>
        null
      // We don't know what is the precise of decimal so we have to assume that it will fit the target type to not block the user
      case (number: Number, _) if Typed.typedClass(number.getClass).canBeSubclassOf(targetType.toTypingResult) =>
        NumberUtils
          .convertNumberToTargetClass[Number](number, targetType.getDefaultConversion.asInstanceOf[Class[Number]])
      case (_, rowType: RowType) =>
        encodeAsRow(value, rowType)
      case (javaMap: java.util.Map[_, _], mapType: MapType) =>
        javaMap.asScala.map { case (key, value) =>
          encode(key, mapType.getKeyType) -> encode(value, mapType.getValueType)
        }.asJava
      case (javaMap: java.util.Map[_, Number @unchecked], multisetType: MultisetType) =>
        javaMap.asScala.map { case (key, value) =>
          encode(key, multisetType.getElementType) -> NumberUtils.convertNumberToTargetClass[Integer](
            value,
            classOf[Integer]
          )
        }.asJava
      case (array: Array[_], arrayType: ArrayType) =>
        encodeAsArray(array, arrayType)
      case (list: java.util.List[Any @unchecked], arrayType: ArrayType) =>
        encodeAsArray(list.asScala, arrayType)
      case (other, _) => other
    }
    if (alignedValue != null && !targetType.supportsInputConversion(alignedValue.getClass)) {
      throw NotConvertibleResultOfAlignmentException(value, alignedValue, targetType)
    }
    alignedValue
  }

  def encodeAsRow(value: Any, rowType: RowType): Row = value match {
    case row: Row =>
      encodeRecord[Row](row, rowType, _.getField)
    case javaMap: java.util.Map[String @unchecked, _] =>
      encodeRecord[java.util.Map[String @unchecked, _]](javaMap, rowType, _.get)
    case _ => throw new IllegalArgumentException("Illegal type for RowType: " + value.getClass)
  }

  private def encodeRecord[RecordType](record: RecordType, rowType: RowType, getField: RecordType => String => Any) = {
    val row = Row.withNames()
    rowType.getFields.asScala.foreach { fieldType =>
      val fieldValue = getField(record)(fieldType.getName)
      row.setField(fieldType.getName, encode(fieldValue, fieldType.getType))
    }
    row
  }

  private def encodeAsArray(list: Seq[Any], arrayType: ArrayType): Array[Any] = {
    list.map(encode(_, arrayType.getElementType)).toArray(ClassTag(arrayType.getElementType.getDefaultConversion))
  }

  def alignTypingResult(typingResult: TypingResult, targetType: LogicalType): TypingResult = {
    (typingResult.withoutValue, targetType) match {
      // TODO: this should behave different depending on ValidationMode - currently we are lax
      case (TypedNull | Unknown, _) =>
        targetType.toTypingResult
      // We don't know what is the precision of decimal so we have to assume that it will fit the target type to not block the user
      case (typ: SingleTypingResult, _)
          if typ.canBeSubclassOf(Typed[Number]) && typ.canBeSubclassOf(targetType.toTypingResult) =>
        targetType.toTypingResult
      case (recordType: TypedObjectTypingResult, rowType: RowType)
          if Set[Class[_]](javaMapClass, rowClass).contains(recordType.objType.klass) =>
        val fields = rowType.getFields.asScala.map { field =>
          val alignedFieldType = {
            recordType.fields
              .get(field.getName)
              .map(alignTypingResult(_, field.getType))
              // TODO: this should behave different depending on ValidationMode - currently we are lax
              .getOrElse(field.getType.toTypingResult)
          }
          field.getName -> alignedFieldType
        }
        Typed.record(fields, Typed.typedClass[Row])
      case (TypedObjectTypingResult(_, TypedClass(`javaMapClass`, keyType :: valueType :: Nil), _), mapType: MapType) =>
        alignMapType(keyType, valueType, mapType)
      case (TypedClass(`javaMapClass`, keyType :: valueType :: Nil), mapType: MapType) =>
        alignMapType(keyType, valueType, mapType)
      case (
            TypedObjectTypingResult(_, TypedClass(`javaMapClass`, keyType :: valueType :: Nil), _),
            multisetType: MultisetType
          ) if valueType.canBeSubclassOf(Typed[Int]) =>
        alignMultisetType(keyType, multisetType)
      case (TypedClass(`javaMapClass`, keyType :: valueType :: Nil), multisetType: MultisetType)
          if valueType.canBeSubclassOf(Typed[Int]) =>
        alignMultisetType(keyType, multisetType)
      case (TypedClass(`arrayClass`, elementType :: Nil), arrayType: ArrayType) =>
        Typed.genericTypeClass(arrayClass, List(alignTypingResult(elementType, arrayType.getElementType)))
      case (TypedClass(`listClass`, elementType :: Nil), arrayType: ArrayType) =>
        Typed.genericTypeClass(arrayClass, List(alignTypingResult(elementType, arrayType.getElementType)))
      case (other, _) =>
        // We fallback to input typing result - some conversions could be done by Flink
        other
    }
  }

  private def alignMapType(keyType: TypingResult, valueType: TypingResult, mapType: MapType) = {
    Typed.genericTypeClass[util.Map[_, _]](
      List(alignTypingResult(keyType, mapType.getKeyType), alignTypingResult(valueType, mapType.getValueType))
    )
  }

  private def alignMultisetType(elementType: TypingResult, multisetType: MultisetType) = {
    TypedMultiset(alignTypingResult(elementType, multisetType.getElementType))
  }

}

case class NotConvertibleResultOfAlignmentException(inputValue: Any, alignedValue: Any, targetType: LogicalType)
    extends Exception(
      s"Value [$inputValue] of class [${inputValue.getClass}] aligned to [$alignedValue] of class [${alignedValue.getClass}] can't be converted to [$targetType]"
    )
