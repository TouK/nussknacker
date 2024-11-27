package pl.touk.nussknacker.engine.process.typeinformation.internal.typedobject

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.CompositeTypeSerializerUtil.IntermediateCompatibilityResult
import org.apache.flink.api.common.typeutils.{
  CompositeTypeSerializerUtil,
  TypeSerializer,
  TypeSerializerSchemaCompatibility,
  TypeSerializerSnapshot
}
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

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
abstract class TypedObjectBasedTypeInformation[T: ClassTag](informations: Array[(String, TypeInformation[_])])
    extends TypeInformation[T] {

  def this(fields: Map[String, TypeInformation[_]]) = {
    this(fields.toArray.sortBy(_._1))
  }

  override def isBasicType: Boolean = false

  override def isTupleType: Boolean = false

  override def getArity: Int = 1

  override def getTotalFields: Int = 1

  override def getTypeClass: Class[T] = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]

  override def isKeyType: Boolean = false

  override def createSerializer(config: ExecutionConfig): TypeSerializer[T] =
    createSerializer(serializers = informations.map { case (k, v) =>
      (k, v.createSerializer(config))
    })

  override def canEqual(obj: Any): Boolean = obj.asInstanceOf[AnyRef].isInstanceOf[TypedObjectBasedTypeInformation[T]]

  def createSerializer(serializers: Array[(String, TypeSerializer[_])]): TypeSerializer[T]
}

//We use Array instead of List here, as we need access by index, which is faster for array
abstract class TypedObjectBasedTypeSerializer[T](val serializers: Array[(String, TypeSerializer[_])])
    extends TypeSerializer[T]
    with LazyLogging {

  protected def name(idx: Int): String = serializers(idx)._1

  protected def serializer(idx: Int): TypeSerializer[_] = serializers(idx)._2

  override def isImmutableType: Boolean = serializers.forall(_._2.isImmutableType)

  override def duplicate(): TypeSerializer[T] = duplicate(serializers.map { case (k, s) =>
    (k, s.duplicate())
  })

  override def copy(from: T): T = from

  // ???
  override def copy(from: T, reuse: T): T = copy(from)

  override def getLength: Int = -1

  override def serialize(record: T, target: DataOutputView): Unit = {
    serializers.foreach { case (key, serializer) =>
      val valueToSerialize = get(record, key)
      // We need marker to allow null values - see e.g. MapSerializer in Flink
      if (valueToSerialize == null) {
        target.writeBoolean(true)
      } else {
        target.writeBoolean(false)
        serializer.asInstanceOf[TypeSerializer[AnyRef]].serialize(valueToSerialize, target)
      }
    }
  }

  override def deserialize(source: DataInputView): T = {
    // TODO: remove array allocation.
    val array: Array[AnyRef] = new Array[AnyRef](serializers.length)
    // We use foreach and not map because: 1. it's faster, 2. it's imperative - we use source.read***
    serializers.indices.foreach { idx =>
      array(idx) = if (!source.readBoolean()) {
        serializer(idx).asInstanceOf[TypeSerializer[AnyRef]].deserialize(source)
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

  override def copy(source: DataInputView, target: DataOutputView): Unit =
    serializers.map(_._2).foreach(_.copy(source, target))

  def snapshotConfiguration(snapshots: Array[(String, TypeSerializerSnapshot[_])]): TypeSerializerSnapshot[T]

  def deserialize(values: Array[AnyRef]): T

  def get(value: T, name: String): AnyRef

  def duplicate(serializers: Array[(String, TypeSerializer[_])]): TypeSerializer[T]
}

abstract class TypedObjectBasedSerializerSnapshot[T] extends TypeSerializerSnapshot[T] with LazyLogging {

  private val constructIntermediateCompatibilityResult
      : (Array[TypeSerializer[_]], Array[TypeSerializerSnapshot[_]]) => IntermediateCompatibilityResult[T] = {
    try {
      // Flink 1.19
      val method = classOf[CompositeTypeSerializerUtil].getMethod(
        "constructIntermediateCompatibilityResult",
        classOf[Array[TypeSerializer[_]]],
        classOf[Array[TypeSerializerSnapshot[_]]],
      )
      (a, b) => method.invoke(null, a, b).asInstanceOf[IntermediateCompatibilityResult[T]]
    } catch {
      // Flink 1.18
      case _: NoSuchMethodException =>
        val method = classOf[CompositeTypeSerializerUtil].getMethod(
          "constructIntermediateCompatibilityResult",
          classOf[Array[TypeSerializerSnapshot[_]]],
          classOf[Array[TypeSerializerSnapshot[_]]],
        )
        (a, b) =>
          method.invoke(null, a.map(_.snapshotConfiguration()), b).asInstanceOf[IntermediateCompatibilityResult[T]]
    }
  }

  protected var serializersSnapshots: Array[(String, TypeSerializerSnapshot[_])] = _

  protected def compatibilityRequiresSameKeys: Boolean

  def this(serializers: Array[(String, TypeSerializerSnapshot[_])]) = {
    this()
    this.serializersSnapshots = serializers
  }

  override def getCurrentVersion: Int = 1

  override def writeSnapshot(out: DataOutputView): Unit = {
    out.writeInt(serializersSnapshots.length)
    serializersSnapshots.foreach { case (k, v) =>
      out.writeUTF(k)
      TypeSerializerSnapshot.writeVersionedSnapshot(out, v)
    }
  }

  override def readSnapshot(readVersion: Int, in: DataInputView, userCodeClassLoader: ClassLoader): Unit = {
    val size = in.readInt()
    serializersSnapshots = (0 until size).map { _ =>
      val key      = in.readUTF()
      val snapshot = TypeSerializerSnapshot.readVersionedSnapshot(in, userCodeClassLoader)
      (key, snapshot)
    }.toArray
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
      val newSerializers       = newSerializerAsTyped.serializers
      val currentKeys          = serializersSnapshots.map(_._1)
      val newKeys              = newSerializers.map(_._1)
      val commons              = currentKeys.intersect(newKeys)

      val newSerializersToUse = newSerializers.filter(k => commons.contains(k._1))
      val snapshotsToUse      = serializersSnapshots.filter(k => commons.contains(k._1))

      val fieldsCompatibility = constructIntermediateCompatibilityResult(
        newSerializersToUse.map(_._2),
        snapshotsToUse.map(_._2)
      )

      // We construct detailed message to show when there are compatibility issues
      def fieldsCompatibilityMessage: String = newSerializersToUse
        .zip(snapshotsToUse)
        .map { case ((name, serializer), (_, snapshot)) =>
          s"$name compatibility is ${snapshot
              .asInstanceOf[TypeSerializerSnapshot[AnyRef]]
              .resolveSchemaCompatibility(serializer.asInstanceOf[TypeSerializer[AnyRef]])}"
        }
        .mkString(", ")

      if (currentKeys sameElements newKeys) {
        if (fieldsCompatibility.isCompatibleAsIs) {
          logger.debug(s"Schema is compatible for keys ${currentKeys.mkString(", ")}")
          TypeSerializerSchemaCompatibility.compatibleAsIs()
        } else if (fieldsCompatibility.isCompatibleWithReconfiguredSerializer) {
          logger.info(s"Schema is compatible after serializer reconfiguration")
          val newSerializer = restoreSerializer(newKeys.zip(fieldsCompatibility.getNestedSerializers))
          TypeSerializerSchemaCompatibility.compatibleWithReconfiguredSerializer(newSerializer)
        } else if (fieldsCompatibility.isCompatibleAfterMigration) {
          logger.info(
            s"Schema migration needed, as fields are equal (${currentKeys.mkString(", ")}), but fields compatibility is [$fieldsCompatibilityMessage] - returning compatibleAfterMigration"
          )
          TypeSerializerSchemaCompatibility.compatibleAfterMigration()
        } else {
          logger.info(
            s"Schema is incompatible, as fields are equal (${currentKeys.mkString(", ")}), but fields compatibility is [$fieldsCompatibilityMessage] - returning incompatible"
          )
          TypeSerializerSchemaCompatibility.incompatible()
        }
      } else {
        if (compatibilityRequiresSameKeys || fieldsCompatibility.isIncompatible) {
          logger.info(
            s"Schema is incompatible, as fields are not equal (old keys: ${currentKeys
                .mkString(", ")}, new keys: ${newKeys.mkString(", ")}), " +
              s" and fields compatibility is [$fieldsCompatibilityMessage] - returning incompatible"
          )
          TypeSerializerSchemaCompatibility.incompatible()
        } else {
          logger.info(
            s"Schema migration needed, as fields are not equal (old keys: ${currentKeys
                .mkString(", ")}, new keys: ${newKeys.mkString(", ")}), " +
              s" fields compatibility is [$fieldsCompatibilityMessage] - returning compatibleAfterMigration"
          )
          TypeSerializerSchemaCompatibility.compatibleAfterMigration()
        }
      }
    }
  }

  override def restoreSerializer(): TypeSerializer[T] = restoreSerializer(serializersSnapshots.map {
    case (k, snapshot) => (k, snapshot.restoreSerializer())
  })

  protected def restoreSerializer(restored: Array[(String, TypeSerializer[_])]): TypeSerializer[T]
}
