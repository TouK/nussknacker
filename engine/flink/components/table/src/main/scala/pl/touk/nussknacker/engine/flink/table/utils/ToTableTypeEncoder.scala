package pl.touk.nussknacker.engine.flink.table.utils

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.core.memory.{DataOutputView, DataOutputViewStreamWrapper}
import org.apache.flink.types.Row
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection

import java.io.ByteArrayOutputStream
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

object ToTableTypeEncoder {

  private val javaMapClass = classOf[java.util.Map[_, _]]

  private val listClass = classOf[java.util.List[_]]

  def encode(value: Any, typingResult: TypingResult): Any = {
    val withoutValue        = typingResult.withoutValue
    val typeInfoWithDetails = TypeInformationDetection.instance.forTypeWithDetails[Any](withoutValue)
    (value, withoutValue) match {
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
      case (other, _) if !typeInfoWithDetails.isTableApiCompatible =>
        val data = new ByteArrayOutputStream(10 * 1024)
        // FIXME serializer cache'ing + passing the correct execution config
        typeInfoWithDetails.typeInformation
          .createSerializer(new ExecutionConfig)
          .serialize(other, new DataOutputViewStreamWrapper(data))
        data.toByteArray.map(_.underlying())
      case (other, _) =>
        other
    }
  }

  def alignTypingResult(typingResult: TypingResult): TypingResult = {
    val withoutValue        = typingResult.withoutValue
    val typeInfoWithDetails = TypeInformationDetection.instance.forTypeWithDetails[Any](withoutValue)
    withoutValue match {
      case recordType @ TypedObjectTypingResult(fields, TypedClass(`javaMapClass`, _), _) =>
        recordType.copy(
          fields = ListMap(fields.toList.map { case (name, value) => name -> alignTypingResult(value) }: _*),
          objType = Typed.typedClass[Row]
        )
      case listType @ TypedClass(`listClass`, elementType :: Nil) =>
        listType.copy(params = alignTypingResult(elementType) :: Nil)
      case _ if !typeInfoWithDetails.isTableApiCompatible =>
        Typed.fromDetailedType[Array[Byte]]
      case other =>
        other
    }
  }

}
