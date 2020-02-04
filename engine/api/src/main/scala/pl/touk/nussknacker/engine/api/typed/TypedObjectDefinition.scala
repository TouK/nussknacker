package pl.touk.nussknacker.engine.api.typed

import pl.touk.nussknacker.engine.api.typed.typing.TypedClass

case class TypedObjectDefinition(fields: Map[String, TypedClass])
