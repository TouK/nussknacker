package pl.touk.nussknacker.engine.management.sample.dto

import io.circe.derivation.annotations.JsonCodec

@JsonCodec case class ConstantState(id: String, transactionId: Int, elements: List[String])
