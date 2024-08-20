import sbt.Keys.runMain
import sbt.{Test, taskKey}

object GenerateScenarioActivityOpenApiPlugin extends sbt.AutoPlugin {
  override def trigger = noTrigger

  object autoImport {
    lazy val generateDesignerOpenApi =
      taskKey[Unit]("Generate Nu Scenario Activity API documentation in OpenAPI format")
  }

  import autoImport.*

  override def projectSettings = Seq(
    generateDesignerOpenApi := {
      (Test / runMain)
        .toTask(
          " pl.touk.nussknacker.ui.api.description.scenarioActivity.SaveScenarioActivityApiToYamlFile docs-internal/api/nu-designer-openapi.yaml"
        )
        .value
    }
  )

}
