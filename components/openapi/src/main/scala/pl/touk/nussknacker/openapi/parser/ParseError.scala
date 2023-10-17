package pl.touk.nussknacker.openapi.parser

import cats.data.NonEmptyList
import pl.touk.nussknacker.openapi.ServiceName

trait ParseError

final case class ServiceParseError(name: ServiceName, errors: NonEmptyList[String]) extends ParseError
