package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.java.typeutils.RowTypeInfo
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{
  SingleTypingResult,
  Typed,
  TypedClass,
  TypedDict,
  TypedNull,
  TypedObjectTypingResult,
  TypedObjectWithValue,
  TypedTaggedValue,
  TypedUnion,
  TypingResult,
  Unknown
}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object RowConversions {

  import scala.jdk.CollectionConverters._

  def contextToRow(context: Context): Row = {
    def scalaMapToRow(map: Map[String, Any]): Row = {
      val row = Row.withNames()
      map.foreach { case (name, value) =>
        row.setField(name, value)
      }
      row
    }
    val row          = Row.withPositions(context.parentContext.map(_ => 3).getOrElse(2))
    val variablesRow = scalaMapToRow(context.variables)
    row.setField(0, context.id)
    row.setField(1, variablesRow)
    context.parentContext.map(contextToRow).foreach(row.setField(2, _))
    row
  }

  def rowToContext(row: Row): Context = {
    def rowToScalaMap(row: Row): Map[String, AnyRef] = {
      val fieldNames = row.getFieldNames(true).asScala
      val fields     = fieldNames.map(n => n -> row.getField(n)).toMap
      fields
    }
    Context(
      row.getField(0).asInstanceOf[String],
      rowToScalaMap(row.getField(1).asInstanceOf[Row]),
      Option(row).filter(_.getArity >= 3).map(_.getField(2).asInstanceOf[Row]).map(rowToContext)
    )
  }

  def toRowNested(value: Any): Any = {
    value match {
      // TODO: Instead of eagerly converting every map, we should check if target type is Row
      case javaMap: java.util.Map[String @unchecked, _] =>
        val row = Row.withNames()
        // We have to keep elements in the same order as specified in TypingResult, see SingleTypingResultExtension.toRowNested
        // because TypingInfo generated based on TypingResult is order-sensitive, see TypingResultAwareTypeInformationDetection
        javaMap.asScala.toList.sortBy(_._1).foreach { case (fieldName, fieldValue) =>
          row.setField(fieldName, toRowNested(fieldValue))
        }
        row
      case _ =>
        value
    }
  }

  implicit class TypeInformationDetectionExtension(typeInformationDetection: TypeInformationDetection) {

    def contextRowTypeInfo(validationContext: ValidationContext): TypeInformation[_] = {
      val (fieldNames, typeInfos) =
        validationContext.localVariables.mapValuesNow(typeInformationDetection.forType).unzip
      val variablesRow = new RowTypeInfo(typeInfos.toArray[TypeInformation[_]], fieldNames.toArray)
      Types.ROW(Types.STRING :: variablesRow :: validationContext.parent.map(contextRowTypeInfo).toList: _*)
    }

  }

  implicit class TypingResultExtension(typingResult: TypingResult) {

    def toRowNested: TypingResult = {
      typingResult match {
        case result: SingleTypingResult => result.toRowNested
        case union: TypedUnion          => Typed(union.possibleTypes.map(_.toRowNested))
        case TypedNull                  => TypedNull
        case Unknown                    => Unknown
      }
    }

  }

  implicit class SingleTypingResultExtension(typingResult: SingleTypingResult) {

    def toRowNested: SingleTypingResult = {
      typingResult match {
        case TypedObjectTypingResult(fields, objType, additionalInfo)
            if objType.klass == classOf[java.util.Map[_, _]] =>
          Typed.record(fields.toList.sortBy(_._1), objType.toRowNested, additionalInfo)
        case notMapBasedRecord: TypedObjectTypingResult => notMapBasedRecord
        case TypedDict(dictId, valueType)               => TypedDict(dictId, valueType.toRowNested)
        case TypedTaggedValue(underlying, tag)          => TypedTaggedValue(underlying.toRowNested, tag)
        case TypedObjectWithValue(underlying, value)    => TypedObjectWithValue(underlying.toRowNested, value)
        case klass: TypedClass                          => klass.toRowNested
      }
    }

  }

  implicit class TypedClassExtension(typedClass: TypedClass) {

    def toRowNested: TypedClass = {
      if (typedClass.klass == classOf[java.util.Map[_, _]]) {
        // TODO: Instead of eagerly converting every map, we should check if target type is Row
        Typed.typedClass[Row]
      } else {
        typedClass
      }
    }

  }

}
