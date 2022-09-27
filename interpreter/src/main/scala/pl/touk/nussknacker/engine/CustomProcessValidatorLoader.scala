package pl.touk.nussknacker.engine

import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.loader.{LoadClassFromClassLoader, ScalaServiceLoader}
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax

object CustomProcessValidatorLoader extends LoadClassFromClassLoader {
  override type ClassToLoad = CustomProcessValidatorFactory
  override val prettyClassName: String = "CustomProcessValidatorLoader"

  def emptyCustomProcessValidator: CustomProcessValidator = {
    new CustomProcessValidatorAggregate(List.empty)
  }

  def loadProcessValidators(classLoader: ClassLoader, config: Config, preloaded: List[CustomProcessValidator] = Nil): CustomProcessValidator = {
    new CustomProcessValidatorAggregate(CustomProcessValidatorLoader.loadAll(classLoader).map(_.validator(config)) ++ preloaded)
  }

  override def loadAll(classLoader: ClassLoader): List[CustomProcessValidatorFactory] = {
    ScalaServiceLoader.load[CustomProcessValidatorFactory](classLoader)
  }

  private class CustomProcessValidatorAggregate(customValidators: List[CustomProcessValidator]) extends CustomProcessValidator {
    private val syntax = ValidatedSyntax[ProcessCompilationError]

    import syntax._
    override def validate(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, Unit] = {
      customValidators.map(_.validate(process)).sequence.map(_ => ())
    }
  }
}
