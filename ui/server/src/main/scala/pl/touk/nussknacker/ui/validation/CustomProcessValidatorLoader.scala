package pl.touk.nussknacker.ui.validation

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.util.loader.{LoadClassFromClassLoader, ScalaServiceLoader}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.validation.{CustomProcessValidator, CustomProcessValidatorFactory}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

object CustomProcessValidatorLoader extends LoadClassFromClassLoader {
  override type ClassToLoad = CustomProcessValidatorFactory
  override val prettyClassName: String = "CustomProcessValidatorLoader"

  def loadProcessValidators(modelData: ModelData, config: Config): CustomProcessValidator = {
    val validators = CustomProcessValidatorLoader.loadAll(modelData.modelClassLoader.classLoader).map(_.validator(config))
    new CustomProcessValidatorAggregate(validators)
  }

  override def loadAll(classLoader: ClassLoader): List[CustomProcessValidatorFactory] = {
    ScalaServiceLoader.load[CustomProcessValidatorFactory](classLoader)
  }

  private class CustomProcessValidatorAggregate(customValidators: List[CustomProcessValidator]) extends CustomProcessValidator {
    override def validate(process: DisplayableProcess): ValidationResult = {
      customValidators.map(_.validate(process))
        .foldLeft(ValidationResult.success)(_ add _)
    }
  }

}