package pl.touk.nussknacker.openapi.parser

import io.swagger.v3.oas.models.{Components, OpenAPI}

import scala.collection.JavaConverters._

private[parser] object ParseSwaggerRefSchemas {

  def apply(parseResult: OpenAPI): SwaggerRefSchemas =
    Option(parseResult.getComponents).map(refSchemas).getOrElse(Map.empty)

  private def refSchemas(components: Components): SwaggerRefSchemas =
    Option(components.getSchemas).map(_.asScala.map { case (name, schema) =>
      s"#/components/schemas/$name" -> schema
    }).getOrElse(Nil).toMap
}
