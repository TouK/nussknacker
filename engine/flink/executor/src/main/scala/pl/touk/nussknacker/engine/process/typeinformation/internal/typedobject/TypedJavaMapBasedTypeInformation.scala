package pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject

import java.{util => jutil}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}

case class TypedJavaMapTypeInformation(
    informations: Map[String, TypeInformation[_]]
) extends TypedObjectBasedTypeInformation[jutil.Map[String, AnyRef]](informations) {

  override def createSerializer(
      serializers: Array[(String, TypeSerializer[_])]
  ): TypeSerializer[jutil.Map[String, AnyRef]] =
    TypedJavaMapSerializer(serializers)

}

@SerialVersionUID(1L)
case class TypedJavaMapSerializer(
    override val serializers: Array[(String, TypeSerializer[_])]
) extends TypedObjectBasedTypeSerializer[jutil.Map[String, AnyRef]](serializers)
    with BaseJavaMapBasedSerializer[AnyRef, jutil.Map[String, AnyRef]] {

  override def duplicate(serializers: Array[(String, TypeSerializer[_])]): TypeSerializer[jutil.Map[String, AnyRef]] =
    TypedJavaMapSerializer(serializers)

  override def createInstance(): jutil.Map[String, AnyRef] = new jutil.HashMap()

  override def snapshotConfiguration(
      snapshots: Array[(String, TypeSerializerSnapshot[_])]
  ): TypeSerializerSnapshot[jutil.Map[String, AnyRef]] = new TypedJavaMapSerializerSnapshot(snapshots)

}

final class TypedJavaMapSerializerSnapshot extends TypedObjectBasedSerializerSnapshot[jutil.Map[String, AnyRef]] {

  def this(serializers: Array[(String, TypeSerializerSnapshot[_])]) = {
    this()
    this.serializersSnapshots = serializers
  }

  override protected def restoreSerializer(
      restored: Array[(String, TypeSerializer[_])]
  ): TypeSerializer[jutil.Map[String, AnyRef]] =
    TypedJavaMapSerializer(restored)

}
