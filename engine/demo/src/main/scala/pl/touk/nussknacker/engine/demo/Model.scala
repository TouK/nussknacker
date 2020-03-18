package pl.touk.nussknacker.engine.demo

import pl.touk.nussknacker.engine.api.{DisplayJsonWithEncoder, Documentation}
import io.circe.generic.JsonCodec

import scala.annotation.meta.getter

@JsonCodec case class Transaction(@(Documentation @getter)(description = "Client id, should be in format: 'Client1'")
  clientId: String, amount: Int, eventDate: Long = System.currentTimeMillis()) extends DisplayJsonWithEncoder[Transaction]
@JsonCodec case class Client(id: String, name: String, cardNumber: String) extends DisplayJsonWithEncoder[Client]

case object DataTypes {
  val Transaction: String = classOf[Transaction].getName
  val Client: String = classOf[Client].getName
}
