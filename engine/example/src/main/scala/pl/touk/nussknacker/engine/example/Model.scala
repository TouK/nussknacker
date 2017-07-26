package pl.touk.nussknacker.engine.example

import pl.touk.nussknacker.engine.api.DisplayableAsJson
import argonaut.ArgonautShapeless._

case class Transaction(clientId: String, amount: Int, eventDate: Long = System.currentTimeMillis()) extends DisplayableAsJson[Transaction]
case class Client(id: String, name: String, cardNumber: String) extends DisplayableAsJson[Client]
