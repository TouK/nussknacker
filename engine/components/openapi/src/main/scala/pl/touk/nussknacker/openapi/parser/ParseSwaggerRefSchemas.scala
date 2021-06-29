package pl.touk.nussknacker.openapi.parser

import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.{Components, OpenAPI}
import pl.touk.nussknacker.openapi.SwaggerRef

import scala.collection.JavaConverters._

object ParseSwaggerRefSchemas {

  def apply(parseResult: OpenAPI): SwaggerRefSchemas =
    Option(parseResult.getComponents).map(refSchemas).getOrElse(Map.empty)

  private def refSchemas(components: Components): SwaggerRefSchemas =
    components.getSchemas.asScala.map { case (name, schema) =>
      s"#/components/schemas/$name" -> schema
    }.toMap
}
