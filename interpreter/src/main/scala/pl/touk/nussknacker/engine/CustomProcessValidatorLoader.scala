package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.loader.{LoadClassFromClassLoader, ScalaServiceLoader}

object CustomProcessValidatorLoader extends LoadClassFromClassLoader {
  override type ClassToLoad = CustomProcessValidatorFactory
  override val prettyClassName: String = "CustomProcessValidatorLoader"

  def emptyCustomProcessValidator: CustomProcessValidator = {
    new CustomProcessValidatorAggregate(List.empty)
  }

  def loadProcessValidators(classLoader: ClassLoader, config: Config): CustomProcessValidator = {
    val validators = CustomProcessValidatorLoader.loadAll(classLoader).map(_.validator(config))
    new CustomProcessValidatorAggregate(validators)
  }

  override def loadAll(classLoader: ClassLoader): List[CustomProcessValidatorFactory] = {
    ScalaServiceLoader.load[CustomProcessValidatorFactory](classLoader)
  }

  private class CustomProcessValidatorAggregate(customValidators: List[CustomProcessValidator]) extends CustomProcessValidator {
    override def validate(process: CanonicalProcess): List[ProcessCompilationError] = {
      customValidators.flatMap(_.validate(process))
    }
  }
}
