package pl.touk.nussknacker.ui.util

import better.files.Dsl._
import pl.touk.nussknacker.ui.api.NuDesignerAvailableToExposeApi

object GenerateDesignerOpenApiYamlFile extends App {

  (pwd / "docs" / "api" / "internal" / "nu-designer-openapi.yaml")
    .createFileIfNotExists(createParents = true)
    .overwrite(
      NuDesignerAvailableToExposeApi.generateOpenApiYaml
    )
}
