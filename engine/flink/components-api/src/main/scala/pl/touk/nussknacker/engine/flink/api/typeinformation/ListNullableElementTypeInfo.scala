package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.serialization.SerializerConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.table.api.DataTypes
import org.apache.flink.table.types.{DataType, DataTypeQueryable}
import org.apache.flink.table.types.utils.TypeInfoDataTypeConverter

import java.util
import scala.annotation.nowarn

/** [[TypeInformation]] for [[java.util.List]] whose elements may be null.
  *
  * Unlike Flink's [[org.apache.flink.api.java.typeutils.ListTypeInfo]], which uses
  * [[org.apache.flink.api.common.typeutils.base.ListSerializer]] that does not support null elements,
  * this class creates a [[ListNullableElementSerializer]] that writes a boolean null-flag before each element.
  *
  * Implements [[DataTypeQueryable]] so that Flink's [[TypeInfoDataTypeConverter]] recognises it as
  * `ARRAY(elementType)` instead of falling back to a RAW type, which allows it to be used in Table API contexts.
  */
class ListNullableElementTypeInfo[T <: AnyRef](val elementTypeInfo: TypeInformation[T])
    extends TypeInformation[util.List[T]]
    with DataTypeQueryable {

  override def getDataType: DataType =
    DataTypes
      .ARRAY(TypeInfoDataTypeConverter.toDataType(null, elementTypeInfo))
      .notNull()
      .bridgedTo(classOf[util.List[_]])

  override def isBasicType: Boolean = false

  override def isTupleType: Boolean = false

  override def getArity: Int = 0

  override def getTotalFields: Int = 1

  override def getTypeClass: Class[util.List[T]] = classOf[util.List[_]].asInstanceOf[Class[util.List[T]]]

  override def isKeyType: Boolean = false

  // TODO: Remove after upgrade to Flink 2.x
  @nowarn("cat=deprecation")
  override def createSerializer(config: ExecutionConfig): TypeSerializer[util.List[T]] =
    createSerializer(config.getSerializerConfig)

  override def createSerializer(config: SerializerConfig): TypeSerializer[util.List[T]] =
    new ListNullableElementSerializer[T](elementTypeInfo.createSerializer(config))

  override def canEqual(obj: Any): Boolean = obj.isInstanceOf[ListNullableElementTypeInfo[_]]

  override def equals(obj: Any): Boolean = obj match {
    case other: ListNullableElementTypeInfo[_] => elementTypeInfo.equals(other.elementTypeInfo)
    case _                                     => false
  }

  override def hashCode(): Int = 31 * elementTypeInfo.hashCode + 1

  override def toString: String = s"ListNullable[$elementTypeInfo]"

}
