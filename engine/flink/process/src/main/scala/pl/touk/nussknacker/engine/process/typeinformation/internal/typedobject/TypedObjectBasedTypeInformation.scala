package pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.{CompositeTypeSerializerUtil, TypeSerializer, TypeSerializerSchemaCompatibility, TypeSerializerSnapshot}
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import org.apache.flink.util.InstantiationUtil

import scala.reflect.ClassTag

/*
  This is base class for handling TypedObjectTypingResult, can be used to handle java Maps, scala Maps, classes based on Row etc.
  Not all TypedObjectTypingResults should be handled by subclasses, e.g. Avro GenericRecord should be handled by
  GenericRecordAvroTypeInfo

  The idea is that we sort fields by name, keep names and respective serializers in TypeSerializerSnapshot,
  and we serialize fields are array. Null values are also handled

  Schema compatibility can have two modes (should be defined by subclasses) - adding/removing fields can either mean:
   - incompatible schema (suitable e.g. for Row)
   - compatible after migration (we ignore removed fields and new fields will have null value)
 */
abstract class TypedObjectBasedTypeInformation[T:ClassTag](informations: List[(String, TypeInformation[_])]) extends TypeInformation[T] {

  def this(fields: Map[String, TypeInformation[_]]) = {
    this(fields.toList.sortBy(_._1))
  }

  override def isBasicType: Boolean = false

  override def isTupleType: Boolean = false

  override def getArity: Int = 1

  override def getTotalFields: Int = 1

  override def getTypeClass: Class[T] = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]

  override def isKeyType: Boolean = false

  override def createSerializer(config: ExecutionConfig): TypeSerializer[T] =
    createSerializer(informations.map {
      case (k, v) => (k, v.createSerializer(config))
    })

  override def canEqual(obj: Any): Boolean = obj.isInstanceOf[TypedObjectBasedTypeInformation[T]]

  def createSerializer(serializers: List[(String, TypeSerializer[_])]): TypeSerializer[T]
}

abstract class TypedObjectBasedTypeSerializer[T](val serializers: List[(String, TypeSerializer[_])]) extends TypeSerializer[T] with LazyLogging {

  protected val names: Array[String] = serializers.map(_._1).toArray

  protected val serializersInstances: Array[TypeSerializer[_]] = serializers.map(_._2).toArray

  override def isImmutableType: Boolean = serializers.forall(_._2.isImmutableType)

  override def duplicate(): TypeSerializer[T] = duplicate(
    serializers.map {
      case (k, s) => (k, s.duplicate())
    })
  
  override def copy(from: T): T = from

  //???
  override def copy(from: T, reuse: T): T = copy(from)

  override def getLength: Int = -1

  override def serialize(record: T, target: DataOutputView): Unit = {
    serializers.foreach { case (key, serializer) =>
      val valueToSerialize = get(record, key)
      if (valueToSerialize == null) {
        target.writeBoolean(true)
      } else {
        target.writeBoolean(false)
        serializer.asInstanceOf[TypeSerializer[AnyRef]].serialize(valueToSerialize, target)
      }
    }
  }

  override def deserialize(source: DataInputView): T = {
    //TODO: remove array allocation
    val array: Array[AnyRef] = new Array[AnyRef](serializers.size)
    serializers.indices.foreach { idx =>
      array(idx) = if (!source.readBoolean()) {
        serializersInstances(idx).asInstanceOf[TypeSerializer[AnyRef]].deserialize(source)
      } else {
        null
      }
    }
    deserialize(array)
  }

  override def snapshotConfiguration(): TypeSerializerSnapshot[T] = snapshotConfiguration(serializers.map {
    case (k, s) => (k, s.snapshotConfiguration())
  })

  override def deserialize(reuse: T, source: DataInputView): T = deserialize(source)

  override def copy(source: DataInputView, target: DataOutputView): Unit = serializers.map(_._2).foreach(_.copy(source, target))

  def snapshotConfiguration(snapshots: List[(String, TypeSerializerSnapshot[_])]): TypeSerializerSnapshot[T]

  def deserialize(values: Array[AnyRef]): T

  def get(value: T, name: String): AnyRef

  def duplicate(serializers: List[(String, TypeSerializer[_])]): TypeSerializer[T]
}

abstract class TypedObjectBasedSerializerSnapshot[T] extends TypeSerializerSnapshot[T] with LazyLogging {

  protected var serializersSnapshots: List[(String, TypeSerializerSnapshot[_])] = _

  protected def nonEqualKeysCompatible: Boolean

  def this(serializers: List[(String, TypeSerializerSnapshot[_])]) = {
    this()
    this.serializersSnapshots = serializers
  }

  override def getCurrentVersion: Int = 0

  override def writeSnapshot(out: DataOutputView): Unit = {
    out.writeInt(serializersSnapshots.size)
    serializersSnapshots.foreach {
      case (k, v) =>
        out.writeUTF(k)
        out.writeUTF(v.getClass.getCanonicalName)
        v.writeSnapshot(out)
    }
  }

  override def readSnapshot(readVersion: Int, in: DataInputView, userCodeClassLoader: ClassLoader): Unit = {
    val size = in.readInt()
    serializersSnapshots = (0 until size).map { _ =>
      val key = in.readUTF()
      val snapshot = InstantiationUtil.resolveClassByName(in, userCodeClassLoader)
        .getConstructor().newInstance().asInstanceOf[TypeSerializerSnapshot[_]]
      snapshot.readSnapshot(1, in, userCodeClassLoader)
      (key, snapshot)
    }.toList
  }

  /*
    The idea is as follows:
    if nonEqualKeysCompatible == true we check if keys present both in new and old serializer are compatible. If
    they are, but key sets are not equals, we return "ready after migration" - so map should be deserialized with old
    serializer and then serialized with new. Some keys would be null, some would be lost.
    if nonEqualKeysCompatible == false we require keys in new and old serializer are the same

   */
  override def resolveSchemaCompatibility(newSerializer: TypeSerializer[T]): TypeSerializerSchemaCompatibility[T] = {
    if (newSerializer.snapshotConfiguration().getClass != getClass) {
      TypeSerializerSchemaCompatibility.incompatible()
    } else {
      val newSerializerAsTyped = newSerializer.asInstanceOf[TypedObjectBasedTypeSerializer[T]]
      val newSerializers = newSerializerAsTyped.serializers
      val currentKeys = serializersSnapshots.map(_._1)
      val newKeys = newSerializers.map(_._1)
      val commons = currentKeys.intersect(newKeys)

      val fieldsCompatibility = CompositeTypeSerializerUtil.constructIntermediateCompatibilityResult(
        newSerializers.filter(k => commons.contains(k._1)).map(_._2).toArray,
        serializersSnapshots.filter(k => commons.contains(k._1)).map(_._2).toArray
      )

      if (currentKeys == newKeys) {
        if (fieldsCompatibility.isCompatibleAsIs) {
          TypeSerializerSchemaCompatibility.compatibleAsIs()
          //TODO: handle serializer reconfiguration more gracefully...
        } else if (fieldsCompatibility.isCompatibleWithReconfiguredSerializer || fieldsCompatibility.isCompatibleAfterMigration) {
          TypeSerializerSchemaCompatibility.compatibleAfterMigration()
        } else TypeSerializerSchemaCompatibility.incompatible()
      } else {
        if (!nonEqualKeysCompatible || fieldsCompatibility.isIncompatible) {
          TypeSerializerSchemaCompatibility.incompatible()
        } else {
          TypeSerializerSchemaCompatibility.compatibleAfterMigration()
        }
      }
    }
  }

  override def restoreSerializer(): TypeSerializer[T] = restoreSerializer(serializersSnapshots.map {
    case (k, snapshot) => (k, snapshot.restoreSerializer())
  })

  protected def restoreSerializer(restored: List[(String, TypeSerializer[_])]): TypeSerializer[T]
}