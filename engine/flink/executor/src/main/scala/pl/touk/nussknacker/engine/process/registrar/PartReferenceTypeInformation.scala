package pl.touk.nussknacker.engine.process.registrar

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.{CompositeTypeSerializerSnapshot, TypeSerializer, TypeSerializerSnapshot}
import org.apache.flink.api.java.typeutils.runtime.kryo.KryoSerializer
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import pl.touk.nussknacker.engine.api._

import java.util.Objects

class PartReferenceTypeInformation extends TypeInformation[PartReference] {

  override def isBasicType: Boolean = true

  override def isTupleType: Boolean = false

  override def getArity: Int = 0

  override def getTotalFields: Int = 0

  override def getTypeClass: Class[PartReference] = classOf[PartReference]

  override def isKeyType: Boolean = false

  @silent("deprecated")
  override def createSerializer(config: ExecutionConfig): TypeSerializer[PartReference] = {
    val fragmentOutputFieldsSerializer = new KryoSerializer[Map[String, Any]](classOf[Map[String, Any]], config)
    new PartReferenceSerializer(fragmentOutputFieldsSerializer)
  }

  override def toString: String =
    s"PartReferenceTypeInformation"

  override def canEqual(obj: Any): Boolean =
    obj.isInstanceOf[PartReferenceTypeInformation]

  override def equals(obj: Any): Boolean =
    canEqual(obj)

  override def hashCode(): Int = 1
}

private object PartReferenceSerializer {
  private val NextPartReferenceType    = 1
  private val JoinReferenceType        = 2
  private val DeadEndReferenceType     = 3
  private val EndReferenceType         = 4
  private val FragmentEndReferenceType = 5
}

// TODO: It's a experimental version, there can be some issues with this serializer
// TODO: For the FragmentEndReference type, fragmentOutputFieldsSerializer field is still serialized by the KryoSerializer :(
class PartReferenceSerializer(val fragmentOutputFieldsSerializer: KryoSerializer[Map[String, Any]])
    extends TypeSerializer[PartReference] {

  import PartReferenceSerializer._

  override def isImmutableType: Boolean = false

  override def duplicate(): TypeSerializer[PartReference] =
    new PartReferenceSerializer(fragmentOutputFieldsSerializer)

  override def createInstance(): PartReference =
    throw new NotImplementedError("There is no base implementation for PartReference")

  override def copy(from: PartReference): PartReference =
    from match {
      case r: NextPartReference =>
        r.copy()
      case r: JoinReference =>
        r.copy()
      case r: DeadEndReference =>
        r.copy()
      case r: EndReference =>
        r.copy()
      case r: FragmentEndReference =>
        r.copy()
    }

  override def copy(from: PartReference, reuse: PartReference): PartReference =
    copy(from)

  override def getLength: Int = -1

  override def copy(source: DataInputView, target: DataOutputView): Unit =
    serialize(deserialize(source), target)

  override def serialize(record: PartReference, target: DataOutputView): Unit =
    record match {
      case r: NextPartReference =>
        target.writeShort(NextPartReferenceType)
        target.writeUTF(r.id)
      case r: JoinReference =>
        target.writeShort(JoinReferenceType)
        target.writeUTF(r.id)
        target.writeUTF(r.joinId)
        target.writeUTF(r.branchId)
      case r: DeadEndReference =>
        target.writeShort(DeadEndReferenceType)
        target.writeUTF(r.nodeId)
      case r: EndReference =>
        target.writeShort(EndReferenceType)
        target.writeUTF(r.nodeId)
      case r: FragmentEndReference =>
        target.writeShort(FragmentEndReferenceType)
        target.writeUTF(r.nodeId)
        fragmentOutputFieldsSerializer.serialize(r.outputFields, target)
    }

  override def deserialize(reuse: PartReference, source: DataInputView): PartReference =
    deserialize(source)

  override def deserialize(source: DataInputView): PartReference = {
    val recordType = source.readShort()
    recordType match {
      case NextPartReferenceType =>
        NextPartReference(source.readUTF())
      case JoinReferenceType =>
        JoinReference(source.readUTF(), source.readUTF(), source.readUTF())
      case DeadEndReferenceType =>
        DeadEndReference(source.readUTF())
      case EndReferenceType =>
        EndReference(source.readUTF())
      case FragmentEndReferenceType =>
        FragmentEndReference(source.readUTF(), fragmentOutputFieldsSerializer.deserialize(source))

    }
  }

  override def snapshotConfiguration(): TypeSerializerSnapshot[PartReference] =
    PartReferenceSerializerSnapshot

  override def equals(obj: Any): Boolean =
    obj.isInstanceOf[PartReferenceSerializer]

  override def hashCode(): Int =
    Objects.hashCode(fragmentOutputFieldsSerializer)
}

object PartReferenceSerializerSnapshot extends CompositeTypeSerializerSnapshot[PartReference, PartReferenceSerializer] {

  override def getCurrentOuterSnapshotVersion: Int = 1

  override def getNestedSerializers(outerSerializer: PartReferenceSerializer): Array[TypeSerializer[_]] =
    Array[TypeSerializer[_]](outerSerializer.fragmentOutputFieldsSerializer)

  override def createOuterSerializerWithNestedSerializers(
      nestedSerializers: Array[TypeSerializer[_]]
  ): PartReferenceSerializer = {
    val fragmentEndReferenceSerializer = nestedSerializers.head.asInstanceOf[KryoSerializer[Map[String, Any]]]
    new PartReferenceSerializer(fragmentEndReferenceSerializer)
  }

}
