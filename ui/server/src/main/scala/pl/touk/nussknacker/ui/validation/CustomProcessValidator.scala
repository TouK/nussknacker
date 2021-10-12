package pl.touk.nussknacker.ui.validation

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.util.loader.{LoadClassFromClassLoader, ScalaServiceLoader}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult

//TODO: this should *NOT* be in restmodel
trait CustomProcessValidatorFactory {
  def validator(config: Config): CustomProcessValidator
}

trait CustomProcessValidator {
  def validate(process: DisplayableProcess): ValidationResult
}

object CustomProcessValidator {
  def apply(modelData: ModelData, config: Config): CustomProcessValidator = new CustomProcessValidator {
    private val customValidators = {
      CustomProcessValidatorLoader.loadAll(modelData.modelClassLoader.classLoader)
        .map(_.validator(config))
    }

    override def validate(process: DisplayableProcess): ValidationResult = {
      customValidators.map(_.validate(process))
        .foldLeft(ValidationResult.success)(_ add _)
    }
  }
}

object CustomProcessValidatorLoader extends LoadClassFromClassLoader {
  override type ClassToLoad = CustomProcessValidatorFactory
  override val prettyClassName: String = "CustomProcessValidatorLoader"

  override def loadAll(classLoader: ClassLoader): List[CustomProcessValidatorFactory] = {
    ScalaServiceLoader.load[CustomProcessValidatorFactory](classLoader)
  }
}


