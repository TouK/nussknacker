package pl.touk.nussknacker.engine.standalone.api.openapi

import io.circe.Json

case class OpenApiSourceDefinition(definition: Json, description: Map[String, String], tags: List[String])