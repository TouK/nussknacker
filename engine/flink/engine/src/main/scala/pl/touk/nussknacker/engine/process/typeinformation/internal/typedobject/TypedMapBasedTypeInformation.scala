package pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}
import pl.touk.nussknacker.engine.api.typed.TypedMap

case class TypedMapTypeInformation(informations: Map[String, TypeInformation[_]]) extends TypedObjectBasedTypeInformation[TypedMap](informations) {
  override def createSerializer(serializers: Array[(String, TypeSerializer[_])]): TypeSerializer[TypedMap] = TypedMapSerializer(serializers)
}

@SerialVersionUID(1L)
case class TypedMapSerializer(override val serializers: Array[(String, TypeSerializer[_])])
  extends TypedObjectBasedTypeSerializer[TypedMap](serializers) with BaseJavaMapBasedSerializer[Any, TypedMap] {

  override def duplicate(serializers: Array[(String, TypeSerializer[_])]): TypeSerializer[TypedMap]
    = TypedMapSerializer(serializers)

  override def createInstance(): TypedMap = new TypedMap()

  override def snapshotConfiguration(snapshots: Array[(String, TypeSerializerSnapshot[_])]): TypeSerializerSnapshot[TypedMap]
    = new TypedMapSerializerSnapshot(snapshots)
}

class TypedMapSerializerSnapshot extends TypedObjectBasedSerializerSnapshot[TypedMap] {

  def this(serializers: Array[(String, TypeSerializerSnapshot[_])]) = {
    this()
    this.serializersSnapshots = serializers
  }

  override protected def compatibilityRequiresSameKeys: Boolean = false

  override protected def restoreSerializer(restored: Array[(String, TypeSerializer[_])]): TypeSerializer[TypedMap]
    = TypedMapSerializer(restored)
}