package pl.touk.nussknacker.engine.api.typed

import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

case class TypedObjectDefinition(fields: Map[String, TypingResult])
