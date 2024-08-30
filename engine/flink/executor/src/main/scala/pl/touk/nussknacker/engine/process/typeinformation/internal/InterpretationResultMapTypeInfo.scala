package pl.touk.nussknacker.engine.process.typeinformation.internal

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}
import org.apache.flink.core.memory.{DataInputView, DataOutputView}
import pl.touk.nussknacker.engine.InterpretationResult
import pl.touk.nussknacker.engine.api.{EndingReference, JoinReference, NextPartReference}
import pl.touk.nussknacker.engine.util.Implicits._

case class InterpretationResultMapTypeInfo(ctx: Map[String, TypeInformation[InterpretationResult]])
    extends TypeInformation[InterpretationResult] {
  override def isBasicType: Boolean = false

  override def isTupleType: Boolean = false

  override def getArity: Int = 1

  override def getTotalFields: Int = 1

  override def getTypeClass: Class[InterpretationResult] = classOf[InterpretationResult]

  override def isKeyType: Boolean = false

  override def createSerializer(config: ExecutionConfig): TypeSerializer[InterpretationResult] =
    InterpretationResultMapTypeSerializer(ctx.mapValuesNow(_.createSerializer(config.getSerializerConfig)))

  override def canEqual(obj: Any): Boolean = obj.isInstanceOf[InterpretationResultMapTypeInfo]
}

case class InterpretationResultMapTypeSerializer(ctx: Map[String, TypeSerializer[InterpretationResult]])
    extends TypeSerializer[InterpretationResult] {

  override def isImmutableType: Boolean = ctx.forall(_._2.isImmutableType)

  override def duplicate(): TypeSerializer[InterpretationResult] = InterpretationResultMapTypeSerializer(
    ctx.mapValuesNow(_.duplicate())
  )

  // ???
  override def createInstance(): InterpretationResult = null

  // ???
  override def copy(from: InterpretationResult): InterpretationResult = from

  override def copy(from: InterpretationResult, reuse: InterpretationResult): InterpretationResult = copy(from)

  override def getLength: Int = -1

  override def serialize(record: InterpretationResult, target: DataOutputView): Unit = {
    val id = record.reference match {
      case NextPartReference(id)   => id
      case JoinReference(id, _, _) => id
      case e: EndingReference      => e.nodeId
    }
    target.writeUTF(id)
    ctx(id).serialize(record, target)
  }

  override def deserialize(source: DataInputView): InterpretationResult = {
    val id = source.readUTF()
    ctx(id).deserialize(source)
  }

  override def deserialize(reuse: InterpretationResult, source: DataInputView): InterpretationResult = {
    val id = source.readUTF()
    ctx(id).deserialize(reuse, source)
  }

  override def copy(source: DataInputView, target: DataOutputView): Unit = {
    val id = source.readUTF()
    target.writeUTF(id)
    ctx(id).copy(source, target)
  }

  override def snapshotConfiguration(): TypeSerializerSnapshot[InterpretationResult] = {
    throw new IllegalArgumentException("InterpretationResult should not be serialized in state")
  }

}
