package pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject

import java.{util => jutil}

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}

case class TypedJavaMapTypeInformation(informations: Map[String, TypeInformation[_]]) extends TypedObjectBasedTypeInformation[java.util.Map[_, _<:AnyRef]](informations) {
  override def createSerializer(serializers: List[(String, TypeSerializer[_])]): TypeSerializer[java.util.Map[_, _<:AnyRef]] = TypedJavaMapSerializer(serializers)
}

case class TypedJavaMapSerializer(override val serializers: List[(String, TypeSerializer[_])])
  extends TypedObjectBasedTypeSerializer[java.util.Map[_, _<:AnyRef]](serializers) with LazyLogging {

  override def deserialize(values: Array[AnyRef]): java.util.Map[_, _<:AnyRef] = {
    val size = serializers.size
    val map = new jutil.HashMap[String, AnyRef](size)
    (0 until size).foreach { idx =>
      map.put(names(idx), values(idx))
    }
    map
  }

  override def get(value: jutil.Map[_, _<:AnyRef], k: String): AnyRef = value.get(k)

  override def duplicate(serializers: List[(String, TypeSerializer[_])]): TypeSerializer[java.util.Map[_, _<:AnyRef]]
    = TypedJavaMapSerializer(serializers)

  override def createInstance(): jutil.Map[_, _<:AnyRef] = new jutil.HashMap()

  override def snapshotConfiguration(snapshots: List[(String, TypeSerializerSnapshot[_])]): TypeSerializerSnapshot[jutil.Map[_, _ <: AnyRef]]
    = new TypedJavaMapSerializerSnapshot(snapshots)
}

class TypedJavaMapSerializerSnapshot extends TypedObjectBasedSerializerSnapshot[java.util.Map[_, _<:AnyRef]] {

  def this(serializers: List[(String, TypeSerializerSnapshot[_])]) = {
    this()
    this.serializersSnapshots = serializers
  }

  override protected def nonEqualKeysCompatible: Boolean = true

  override protected def restoreSerializer(restored: List[(String, TypeSerializer[_])]): TypeSerializer[jutil.Map[_, _ <: AnyRef]]
    = TypedJavaMapSerializer(restored)
}