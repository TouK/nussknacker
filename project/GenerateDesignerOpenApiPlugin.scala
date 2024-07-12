import sbt.Keys.runMain
import sbt.{Test, taskKey}

object GenerateDesignerOpenApiPlugin extends sbt.AutoPlugin {
  override def trigger = noTrigger

  object autoImport {
    lazy val generateDesignerOpenApi = taskKey[Unit]("Generate Nu Designer API documentation in OpenAPI format")
  }

  import autoImport._

  override def projectSettings = Seq(
    generateDesignerOpenApi := {
      (Test / runMain)
        .toTask(
          " pl.touk.nussknacker.test.utils.GenerateDesignerOpenApiYamlFile docs-internal/api/nu-designer-openapi.yaml"
        )
        .value
    }
  )

}
