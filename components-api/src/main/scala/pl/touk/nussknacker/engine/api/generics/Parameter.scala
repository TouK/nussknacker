package pl.touk.nussknacker.engine.api.generics

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}

case class Parameter(name: String, refClazz: TypingResult)

