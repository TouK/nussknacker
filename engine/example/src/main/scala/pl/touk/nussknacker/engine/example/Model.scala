package pl.touk.nussknacker.engine.example

import pl.touk.nussknacker.engine.api.{DisplayableAsJson, Documentation}
import argonaut.ArgonautShapeless._

import scala.annotation.meta.getter

case class Transaction(@(Documentation @getter)(description = "Client id, should be in format: 'Client1'")
  clientId: String, amount: Int, eventDate: Long = System.currentTimeMillis()) extends DisplayableAsJson[Transaction]
case class Client(id: String, name: String, cardNumber: String) extends DisplayableAsJson[Client]

case object DataTypes {
  val Transaction: String = classOf[Transaction].getName
  val Client: String = classOf[Client].getName
}