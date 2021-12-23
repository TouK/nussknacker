package pl.touk.nussknacker.engine.lite.kafka.api

import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.{Context, VariableConstants}
import pl.touk.nussknacker.engine.lite.api.utils.sources.BaseLiteSource

import scala.language.higherKinds

trait LiteKafkaSource extends BaseLiteSource[ConsumerRecord[Array[Byte], Array[Byte]]] {

  def topics: List[String]

}
