package pl.touk.nussknacker.engine.kafka.source

import io.circe.Json
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

case class KafkaTestParametersInfo(uiParameters: List[Parameter], schemaId: Option[String], formatMessage: Any => Json)

object KafkaTestParametersInfo {

  def empty: KafkaTestParametersInfo = KafkaTestParametersInfo(Nil, None, _ => Json.Null)

  def wrapWithConsumerData(testData: Json, schemaId: Option[String], topic: String): Json = {
    BestEffortJsonEncoder.defaultForTests.encode(
      Map(
      "keySchemaId" -> null,
      "valueSchemaId" -> schemaId,
      "consumerRecord" -> Map(
        "key" -> null,
        "value" -> testData,
        "topic" -> topic,
        "partition" -> -1,
        "offset" -> -1L,
        "timestamp" -> -1L,
        "timestampType" -> TimestampType.NO_TIMESTAMP_TYPE.name,
        "headers" -> Map.empty,
        "leaderEpoch" -> null
      ))
    )
  }

}