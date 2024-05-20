package pl.touk.nussknacker.test.utils

import io.circe.ACursor
import io.circe.yaml.{parser => YamlParser}

object OpenAPISchemaComponents {

  def enumValues(specYaml: String, enumName: String): Set[String] = {
    component(specYaml, enumName)
      .downField("enum")
      .focus
      .toList
      .flatMap(_.asArray)
      .flatMap(_.flatMap(_.asString))
      .toSet
  }

  private def component(specYaml: String, componentName: String): ACursor = {
    YamlParser
      .parse(specYaml)
      .toOption
      .get
      .hcursor
      .downField("components")
      .downField("schemas")
      .downField(componentName)
  }

}
