import sbt.Keys.runMain
import sbt.taskKey
import sbt.Compile

object GenerateDesignerOpenApiPlugin extends sbt.AutoPlugin {
  override def trigger = noTrigger

  object autoImport {
    lazy val generateDesignerOpenApi = taskKey[Unit]("Generate Nu Designer API documentation in OpenAPI format")
  }

  import autoImport._

  override def projectSettings = Seq(
    generateDesignerOpenApi := {
      (Compile / runMain)
        .toTask(
          " pl.touk.nussknacker.ui.util.GenerateDesignerOpenApiYamlFile docs-internal/api/nu-designer-openapi.yaml"
        )
        .value
    }
  )
}
