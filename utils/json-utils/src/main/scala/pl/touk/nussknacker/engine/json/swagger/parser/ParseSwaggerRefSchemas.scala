package pl.touk.nussknacker.engine.json.swagger.parser

import io.swagger.v3.oas.models.{Components, OpenAPI}

import scala.jdk.CollectionConverters.mapAsScalaMapConverter

object ParseSwaggerRefSchemas {

  def apply(parseResult: OpenAPI): SwaggerRefSchemas =
    Option(parseResult.getComponents).map(refSchemas).getOrElse(Map.empty)

  private def refSchemas(components: Components): SwaggerRefSchemas =
    components.getSchemas.asScala.map { case (name, schema) =>
      s"#/components/schemas/$name" -> schema
    }.toMap
}
