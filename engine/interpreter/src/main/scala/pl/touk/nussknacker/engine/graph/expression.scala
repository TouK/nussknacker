package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec

import scala.language.implicitConversions

object expression {

  @JsonCodec case class Expression(language: String, expression: String)

}