package pl.touk.nussknacker.engine.standalone.api.openapi

import io.circe.Json

case class OpenApiSourceDefinition(definition: Json, description: String, tags: List[String])