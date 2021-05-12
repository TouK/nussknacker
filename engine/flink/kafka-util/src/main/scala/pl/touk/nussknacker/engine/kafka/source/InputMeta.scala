package pl.touk.nussknacker.engine.kafka.source

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.java.typeutils.MapTypeInfo
import org.apache.flink.api.scala.typeutils.{CaseClassTypeInfo, ScalaCaseClassSerializer}
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult}
import pl.touk.nussknacker.engine.flink.api.typeinformation.{TypeInformationDetectionForTypingResult, TypingResultAwareTypeInformationCustomisation}

import scala.collection.immutable.ListMap

/**
  * InputMeta represents kafka event metadata. It is based on [[org.apache.kafka.clients.consumer.ConsumerRecord]].
  * Ignored fields: checksum, serializedKeySize, serializedValueSize.
  *
  * @param key - event key
  * @param topic - kafka topic
  * @param partition - kafka partition
  * @param offset - event offset
  * @param timestamp - event timestamp
  * @param timestampType - see [[org.apache.kafka.common.record.TimestampType]]
  * @param headers - event headers converted to map
  * @param leaderEpoch - number of leaders previously assigned by the controller (> 0 indicates leader failure)
  * @tparam K - type of event key
  */
case class InputMeta[K](key: K,
                        topic: String,
                        partition: Integer,
                        offset: java.lang.Long,
                        timestamp: java.lang.Long,
                        timestampType: TimestampType,
                        headers: java.util.Map[String, String],
                        leaderEpoch: Integer
                       ) extends BaseInputMeta

object InputMeta {

  val keyParameterName: String = "key"

  /**
    * Provides definition of whole metadata object, with given key type definition (keyTypingResult).
    * Here "BaseInputMeta" is used to allow proper type information detection when type erasing occurs.
    * See also [[InputMetaAwareTypeInformationCustomisation]]
    */
  def withType(keyTypingResult: typing.TypingResult): typing.TypingResult =
    TypedObjectTypingResult(
      ListMap(
        keyParameterName -> keyTypingResult,
        "topic" -> Typed[String],
        "partition" -> Typed[Integer],
        "offset" -> Typed[java.lang.Long],
        "timestamp" -> Typed[java.lang.Long],
        "timestampType" -> Typed[TimestampType],
        "headers" -> Typed[java.util.Map[String, String]],
        "leaderEpoch" -> Typed[Integer]
      ),
      Typed.typedClass[BaseInputMeta])

  def typeInformation[K](keyTypeInformation: TypeInformation[K]): CaseClassTypeInfo[InputMeta[K]] = {
    val fieldNames = List(
      keyParameterName,
      "topic",
      "partition",
      "offset",
      "timestamp",
      "timestampType",
      "headers",
      "leaderEpoch"
    )
    val fieldTypes = List(
      keyTypeInformation,
      TypeInformation.of(classOf[String]),
      TypeInformation.of(classOf[Integer]),
      TypeInformation.of(classOf[java.lang.Long]),
      TypeInformation.of(classOf[java.lang.Long]),
      TypeInformation.of(classOf[TimestampType]),
      new MapTypeInfo(classOf[String], classOf[String]),
      TypeInformation.of(classOf[Integer])
    )
    new CaseClassTypeInfo[InputMeta[K]](classOf[InputMeta[K]], Array.empty, fieldTypes, fieldNames){
      override def createSerializer(config: ExecutionConfig): TypeSerializer[InputMeta[K]] =
        new ScalaCaseClassSerializer[InputMeta[K]](classOf[InputMeta[K]], fieldTypes.map(_.createSerializer(config)).toArray)
    }
  }
}

/**
  * BaseInputMeta keeps definition of metadata fields to extract TypingResult.
  */
trait BaseInputMeta {
  def topic: String
  def partition: Integer
  def offset: java.lang.Long
  def timestamp: java.lang.Long
  def timestampType: TimestampType
  def headers: java.util.Map[String, String]
  def leaderEpoch: Integer
}

/**
  * Customisation for TypeInformationDetection that provides type information for BaseInputMeta.
  */
class InputMetaAwareTypeInformationCustomisation extends TypingResultAwareTypeInformationCustomisation {
  override def customise(originalDetection: TypeInformationDetectionForTypingResult): PartialFunction[typing.TypingResult, TypeInformation[_]] = {
    case a:TypedObjectTypingResult if a.objType.klass == classOf[BaseInputMeta] =>
      InputMeta.typeInformation(originalDetection.forType(a.fields(InputMeta.keyParameterName)))
  }

}