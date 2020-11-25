package pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}

case class TypedScalaMapTypeInformation(informations: Map[String, TypeInformation[_]]) extends TypedObjectBasedTypeInformation[Map[String, _<:AnyRef]](informations) {
  override def createSerializer(serializers: List[(String, TypeSerializer[_])]): TypeSerializer[Map[String, _<:AnyRef]] = TypedScalaMapSerializer(serializers)
}

case class TypedScalaMapSerializer(override val serializers: List[(String, TypeSerializer[_])])
  extends TypedObjectBasedTypeSerializer[Map[String, _<:AnyRef]](serializers) with LazyLogging {

  override def deserialize(values: Array[AnyRef]): Map[String, _<:AnyRef] = {
    //TODO: faster MapBuilder?
    val builder = Map.newBuilder[String, AnyRef]
    serializers.indices.foreach { idx =>
      builder.+=((names(idx), values(idx)))
    }
    builder.result()
  }

  override def get(value: Map[String, _<:AnyRef], k: String): AnyRef = value.getOrElse(k, null)

  override def duplicate(serializers: List[(String, TypeSerializer[_])]): TypeSerializer[Map[String, _<:AnyRef]]
    = TypedScalaMapSerializer(serializers)

  override def createInstance(): Map[String, _<:AnyRef] = Map.empty

  override def snapshotConfiguration(snapshots: List[(String, TypeSerializerSnapshot[_])]): TypeSerializerSnapshot[Map[String, _ <: AnyRef]]
    = new TypedScalaMapSerializerSnapshot(snapshots)
}

class TypedScalaMapSerializerSnapshot extends TypedObjectBasedSerializerSnapshot[Map[String, _<:AnyRef]] {

  def this(serializers: List[(String, TypeSerializerSnapshot[_])]) = {
    this()
    this.serializersSnapshots = serializers
  }

  override protected def nonEqualKeysCompatible: Boolean = true

  override protected def restoreSerializer(restored: List[(String, TypeSerializer[_])]): TypeSerializer[Map[String, _ <: AnyRef]]
    = TypedScalaMapSerializer(restored)
}