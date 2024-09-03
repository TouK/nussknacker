package pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}

case class TypedScalaMapTypeInformation(informations: Map[String, TypeInformation[_]])
    extends TypedObjectBasedTypeInformation[Map[String, _ <: AnyRef]](informations) {

  override def createSerializer(
      serializers: Array[(String, TypeSerializer[_])]
  ): TypeSerializer[Map[String, _ <: AnyRef]] = TypedScalaMapSerializer(serializers)

}

@SerialVersionUID(1L)
case class TypedScalaMapSerializer(override val serializers: Array[(String, TypeSerializer[_])])
    extends TypedObjectBasedTypeSerializer[Map[String, _ <: AnyRef]](serializers)
    with LazyLogging {

  override def deserialize(values: Array[AnyRef]): Map[String, _ <: AnyRef] = {
    // using builder instead of zipWithIndex.map.toMap gives 10-20% performance improvement
    val builder = Map.newBuilder[String, AnyRef]
    serializers.indices.foreach { idx =>
      builder.+=((name(idx), values(idx)))
    }
    builder.result()
  }

  override def get(value: Map[String, _ <: AnyRef], k: String): AnyRef = value.getOrElse(k, null)

  override def duplicate(serializers: Array[(String, TypeSerializer[_])]): TypeSerializer[Map[String, _ <: AnyRef]] =
    TypedScalaMapSerializer(serializers)

  override def createInstance(): Map[String, _ <: AnyRef] = Map.empty

  override def snapshotConfiguration(
      snapshots: Array[(String, TypeSerializerSnapshot[_])]
  ): TypeSerializerSnapshot[Map[String, _ <: AnyRef]] = new TypedScalaMapSerializerSnapshot(snapshots)

}

class TypedScalaMapSerializerSnapshot extends TypedObjectBasedSerializerSnapshot[Map[String, _ <: AnyRef]] {

  def this(serializers: Array[(String, TypeSerializerSnapshot[_])]) = {
    this()
    this.serializersSnapshots = serializers
  }

  override protected def restoreSerializer(
      restored: Array[(String, TypeSerializer[_])]
  ): TypeSerializer[Map[String, _ <: AnyRef]] = TypedScalaMapSerializer(restored)

}
