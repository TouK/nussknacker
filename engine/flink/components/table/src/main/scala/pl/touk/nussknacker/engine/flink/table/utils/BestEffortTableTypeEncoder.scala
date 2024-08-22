package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

object BestEffortTableTypeEncoder {

  private val javaMapClass = classOf[java.util.Map[_, _]]

  private val listClass = classOf[java.util.List[_]]

  def encode(value: Any, typingResult: TypingResult): Any = {
    (value, typingResult.withoutValue) match {
      case (
            javaMap: java.util.Map[String @unchecked, _],
            TypedObjectTypingResult(fields, TypedClass(`javaMapClass`, _), _)
          ) =>
        val row = Row.withNames()
        javaMap.asScala.foreach { case (fieldName, fieldValue) =>
          val encodedFieldValue = fields.get(fieldName).map(encode(fieldValue, _)).getOrElse(fieldValue)
          row.setField(fieldName, encodedFieldValue)
        }
        row
      case (javaList: java.util.List[_], TypedClass(`listClass`, elementType :: Nil)) =>
        javaList.asScala.map(encode(_, elementType)).asJava
      case (other, _) =>
        other
    }
  }

  def alignTypingResult(typingResult: TypingResult): TypingResult = {
    typingResult.withoutValue match {
      case recordType @ TypedObjectTypingResult(fields, TypedClass(`javaMapClass`, _), _) =>
        recordType.copy(
          fields = ListMap(fields.toList.map { case (name, value) => name -> alignTypingResult(value) }: _*),
          objType = Typed.typedClass[Row]
        )
      case listType @ TypedClass(`listClass`, elementType :: Nil) =>
        listType.copy(params = alignTypingResult(elementType) :: Nil)
      case other =>
        other
    }
  }

}
