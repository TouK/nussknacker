package pl.touk.nussknacker.engine.flink.table.typing

import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.typed.StandardTypesClasses.ListClass
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

object ToTableTypeEncoder {

  def encode(value: Any, typingResult: TypingResult): Any = {
    (value, typingResult.withoutValue) match {
      case (_, TypedObjectTypingResult(fields, _, _)) =>
        val row = Row.withNames()
        fields.foreach { case (fieldName, fieldType) =>
          val encodedFieldValue = encode(extractField(value, fieldName), fieldType)
          row.setField(fieldName, encodedFieldValue)
        }
        row
      case (javaList: java.util.List[_], TypedClass(`ListClass`, elementType :: Nil)) =>
        javaList.asScala.map(encode(_, elementType)).asJava
      case (other, _) =>
        other
    }
  }

  // Extracts field value from record-like objects similarly to MapLikePropertyAccessor in SpEL property accessors
  private def extractField(value: Any, fieldName: String): Any = value match {
    case m: java.util.Map[_, _] => m.get(fieldName)
    case other                  => findGetMethod(other.getClass).invoke(other, fieldName)
  }

  private val getMethodCache = new ConcurrentHashMap[Class[_], Method]()

  private def findGetMethod(clazz: Class[_]): Method = {
    getMethodCache.computeIfAbsent(
      clazz,
      cls =>
        cls.getMethods
          .find(m =>
            (m.getName == "get" || m.getName == "getField") &&
              (m.getParameterTypes sameElements Array(classOf[String]))
          )
          .getOrElse(
            throw new IllegalArgumentException(
              s"Cannot extract fields from ${cls.getName}: no get(String) or getField(String) method found"
            )
          )
    )
  }

  def alignTypingResult(typingResult: TypingResult): TypingResult = {
    typingResult.withoutValue match {
      case recordType @ TypedObjectTypingResult(fields, _, _) =>
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
