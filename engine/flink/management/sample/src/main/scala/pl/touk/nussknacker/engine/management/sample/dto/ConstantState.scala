package pl.touk.nussknacker.engine.management.sample.dto

import io.circe.generic.JsonCodec

@JsonCodec case class ConstantState(id: String, transactionId: Int, elements: List[String])
