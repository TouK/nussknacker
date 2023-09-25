package pl.touk.nussknacker.ui.api

import better.files.Dsl._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.ui.services.NuDesignerApiAvailableToExpose

// if the test fails it probably means that you should regenerate the Nu Designer OpenAPI document
// you can do it but running manually the object `GenerateDesignerOpenApiYamlFile` with main method or
// using SBT's task: `sbt generateDesignerOpenApi`
class NuDesignerApiAvailableToExposeYamlSpec extends AnyFunSuite with Matchers {

  test("Nu Designer OpenAPI document with all available to expose endpoints has to be up to date") {
    val currentNuDesignerOpenApiYamlContent = (pwd / "docs" / "api" / "internal" / "nu-designer-openapi.yaml").contentAsString
    NuDesignerApiAvailableToExpose.generateOpenApiYaml should be (currentNuDesignerOpenApiYamlContent)
  }
}
