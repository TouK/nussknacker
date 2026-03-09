package pl.touk.nussknacker.engine.flink.table.typing

import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses.{ListClass, MapClass}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

object ToTableTypeEncoder {

  def encode(value: Any, typingResult: TypingResult): Any = {
    (value, typingResult.withoutValue) match {
      case (
            javaMap: java.util.Map[String @unchecked, _],
            TypedObjectTypingResult(fields, TypedClass(`MapClass`, _), _)
          ) =>
        val row = Row.withNames()
        javaMap.asScala.foreach { case (fieldName, fieldValue) =>
          val encodedFieldValue = fields.get(fieldName).map(encode(fieldValue, _)).getOrElse(fieldValue)
          row.setField(fieldName, encodedFieldValue)
        }
        row
      case (javaList: java.util.List[_], TypedClass(`ListClass`, elementType :: Nil)) =>
        javaList.asScala.map(encode(_, elementType)).asJava
      case (other, _) =>
        other
    }
  }

  def alignTypingResult(typingResult: TypingResult): TypingResult = {
    typingResult.withoutValue match {
      case recordType @ TypedObjectTypingResult(fields, TypedClass(`MapClass`, _), _) =>
        recordType.copy(
          fields = ListMap(fields.toList.map { case (name, value) => name -> alignTypingResult(value) }: _*),
          runtimeObjType = Typed.typedClass[Row]
        )
      case listType @ TypedClass(`ListClass`, elementType :: Nil) =>
        listType.copy(params = alignTypingResult(elementType) :: Nil)
      case other =>
        other
    }
  }

}
