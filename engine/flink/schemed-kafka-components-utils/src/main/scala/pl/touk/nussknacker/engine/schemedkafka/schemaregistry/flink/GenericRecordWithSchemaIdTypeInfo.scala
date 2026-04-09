package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import org.apache.flink.api.common.serialization.SerializerConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.GenericRecordWithSchemaId

@SerialVersionUID(0L)
class GenericRecordWithSchemaIdTypeInfo extends TypeInformation[GenericRecordWithSchemaId] {

  override def isBasicType: Boolean = false

  override def isTupleType: Boolean = false

  override def getArity: Int = 1

  override def getTotalFields: Int = 1

  override def getTypeClass: Class[GenericRecordWithSchemaId] = classOf[GenericRecordWithSchemaId]

  override def isKeyType: Boolean = false

  override def createSerializer(config: SerializerConfig): TypeSerializer[GenericRecordWithSchemaId] =
    new GenericRecordWithSchemaIdSerializer

  override def canEqual(obj: Any): Boolean = obj.isInstanceOf[GenericRecordWithSchemaId]

  override def toString: String = getClass.getSimpleName

  override def equals(obj: Any): Boolean = {
    obj match {
      case info: GenericRecordWithSchemaIdTypeInfo => info.canEqual(obj)
      case _                                       => false
    }
  }

  override def hashCode(): Int = getClass.hashCode()
}
