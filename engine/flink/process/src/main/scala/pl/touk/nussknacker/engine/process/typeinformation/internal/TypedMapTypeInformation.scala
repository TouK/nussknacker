package pl.touk.nussknacker.engine.process.typeinformation.internal

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.{TypeSerializer, TypeSerializerSnapshot}
import org.apache.flink.core.memory.{DataInputView, DataOutputView}

import scala.collection.SortedMap
import scala.collection.immutable.TreeMap
import pl.touk.nussknacker.engine.util.Implicits._

//TODO: handle other classes e.g. java.util.Map, avro...
case class TypedMapTypeInformation(informations: Map[String, TypeInformation[_]]) extends TypeInformation[Map[String, AnyRef]] {

  override def isBasicType: Boolean = false

  override def isTupleType: Boolean = false

  override def getArity: Int = 1

  override def getTotalFields: Int = 1

  override def getTypeClass: Class[Map[String, AnyRef]] = classOf[Map[String, AnyRef]]

  override def isKeyType: Boolean = false

  override def createSerializer(config: ExecutionConfig): TypeSerializer[Map[String, AnyRef]] =
    TypedMapSerializer(TreeMap[String, TypeSerializer[_]]() ++ informations.mapValuesNow(_.createSerializer(config)))

  override def canEqual(obj: Any): Boolean = obj.isInstanceOf[TypedMapTypeInformation]

}

case class TypedMapSerializer(serializers: SortedMap[String, TypeSerializer[_]]) extends TypeSerializer[Map[String, AnyRef]] with LazyLogging {

  override def isImmutableType: Boolean = serializers.forall(_._2.isImmutableType)

  override def duplicate(): TypeSerializer[Map[String, AnyRef]] = TypedMapSerializer(TreeMap(
    serializers.mapValues(_.duplicate()).toArray: _*))

  override def createInstance(): Map[String, AnyRef] = Map()

  override def copy(from: Map[String, AnyRef]): Map[String, AnyRef] = from.collect {
    case (k, v) if serializers.contains(k) =>
      k -> serializers(k).asInstanceOf[TypeSerializer[AnyRef]].copy(v.asInstanceOf[AnyRef])
  }

  //???
  override def copy(from: Map[String, AnyRef], reuse: Map[String, AnyRef]): Map[String, AnyRef] = copy(from)

  override def getLength: Int = -1

  override def serialize(record: Map[String, AnyRef], target: DataOutputView): Unit = {
    serializers.foreach { case (k, v) =>
      val valueToSerialize = record.get(k).orNull
      v.asInstanceOf[TypeSerializer[AnyRef]].serialize(valueToSerialize, target)
    }
    if (record.keySet != serializers.keySet) {
      logger.warn(s"Different keys in TypedObject serialization, in record: ${record.keySet}, in definition: ${serializers.keySet}")
    }
  }

  override def deserialize(source: DataInputView): Map[String, AnyRef] = {
    serializers.foldLeft(Map[String, AnyRef]()) {
      case (acc, (k, v)) => acc + (k -> v.asInstanceOf[TypeSerializer[AnyRef]].deserialize(source))
    }
  }

  override def deserialize(reuse: Map[String, AnyRef], source: DataInputView): Map[String, AnyRef] = deserialize(source)

  override def copy(source: DataInputView, target: DataOutputView): Unit = serializers.values.foreach(_.copy(source, target))

  //TODO...
  override def snapshotConfiguration(): TypeSerializerSnapshot[Map[String, AnyRef]] = ???
}