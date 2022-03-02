package pl.touk.nussknacker.engine.graph.expression

import io.circe.generic.JsonCodec

@JsonCodec case class Expression(language: String, expression: String)