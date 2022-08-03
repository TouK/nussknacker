package pl.touk.nussknacker.engine.json.swagger

import io.swagger.v3.oas.models.media.Schema

package object parser {

  type PropertyName = String

  type SwaggerRef = String

  type SwaggerRefSchemas = Map[SwaggerRef, Schema[_]]


}
