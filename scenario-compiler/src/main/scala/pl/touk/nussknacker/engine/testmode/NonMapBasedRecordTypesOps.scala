package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.util.Implicits.RichScalaListMap

import java.util.{Map => JMap}

object NonMapBasedRecordTypesOps {

  implicit class TypingResultExt(typingResult: TypingResult) {

    // This method translates nom-Map-based record types (e.g. based on GenericRecord or Row) into Map-based records
    def toMapBasedRecordTypes: TypingResult = typingResult match {
      case union: TypedUnion          => Typed(union.possibleTypes.map(_.toMapBasedRecordTypes))
      case TypedNull                  => TypedNull
      case single: SingleTypingResult => single.toMapBasedRecordTypes
      case unknown: Unknown           => unknown
    }

  }

  private implicit class SingleTypingResultExt(single: SingleTypingResult) {

    def toMapBasedRecordTypes: SingleTypingResult = single match {
      case record @ TypedObjectTypingResult(fields, runtimeObjType, _)
          if runtimeObjType.klass == classOf[JMap[String @unchecked, _]] =>
        val newFields = fields.mapValuesNow(_.toMapBasedRecordTypes)
        // We don't want to change record if fields wasn't changed - see InputMeta.withType
        if (newFields != fields)
          record.copy(fields = newFields)
        else
          record
      case TypedObjectTypingResult(fields, _, _) =>
        Typed.record(fields.mapValuesNow(_.toMapBasedRecordTypes).toList)
      case dict @ TypedDict(_, valueType)           => dict.copy(valueType = valueType.toMapBasedRecordTypes)
      case tagged @ TypedTaggedValue(underlying, _) => tagged.copy(underlying.toMapBasedRecordTypes)
      case withValue @ TypedObjectWithValue(underlying, _) =>
        val newUnderlying = underlying.toMapBasedRecordTypes
        if (newUnderlying != underlying) {
          throw new IllegalArgumentException(
            "Translation of types with value containing nom-Map-based record to Map-based-record types is not allowed"
          )
        } else {
          withValue
        }
      case clazz: TypedClass => clazz.toMapBasedRecordTypes
    }

  }

  private implicit class TypedClassExt(clazz: TypedClass) {
    def toMapBasedRecordTypes: TypedClass = clazz.copy(params = clazz.params.map(_.toMapBasedRecordTypes))
  }

}
