package pl.touk.nussknacker.restmodel.process

import io.circe.generic.JsonCodec

@JsonCodec case class ProcessVersionId(value: Long) extends AnyVal
