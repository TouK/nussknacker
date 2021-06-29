package pl.touk.nussknacker.openapi

import io.swagger.v3.oas.models.media.Schema

package object parser {

  type SwaggerRefSchemas = Map[SwaggerRef, Schema[_]]


}
