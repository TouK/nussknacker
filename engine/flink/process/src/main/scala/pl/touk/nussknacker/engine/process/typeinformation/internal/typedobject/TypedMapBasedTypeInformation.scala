package pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject

import java.util

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}
import pl.touk.nussknacker.engine.api.typed.TypedMap

case class TypedMapTypeInformation(informations: Map[String, TypeInformation[_]]) extends TypedObjectBasedTypeInformation[TypedMap](informations) {
  override def createSerializer(serializers: List[(String, TypeSerializer[_])]): TypeSerializer[TypedMap] = TypedMapSerializer(serializers)
}

case class TypedMapSerializer(override val serializers: List[(String, TypeSerializer[_])])
  extends TypedObjectBasedTypeSerializer[TypedMap](serializers) with LazyLogging {

  override def deserialize(values: Array[AnyRef]): TypedMap = {
    val size = serializers.size
    val map = new TypedMap()
    (0 until size).foreach { idx =>
      map.put(names(idx), values(idx))
    }
    map
  }

  override def get(value: TypedMap, k: String): AnyRef = value.get(k).asInstanceOf[AnyRef]

  override def duplicate(serializers: List[(String, TypeSerializer[_])]): TypeSerializer[TypedMap]
    = TypedMapSerializer(serializers)

  override def createInstance(): TypedMap = new TypedMap()

  override def snapshotConfiguration(snapshots: List[(String, TypeSerializerSnapshot[_])]): TypeSerializerSnapshot[TypedMap]
    = new TypedMapSerializerSnapshot(snapshots)
}

class TypedMapSerializerSnapshot extends TypedObjectBasedSerializerSnapshot[TypedMap] {

  def this(serializers: List[(String, TypeSerializerSnapshot[_])]) = {
    this()
    this.serializersSnapshots = serializers
  }

  override protected def nonEqualKeysCompatible: Boolean = true

  override protected def restoreSerializer(restored: List[(String, TypeSerializer[_])]): TypeSerializer[TypedMap]
    = TypedMapSerializer(restored)
}