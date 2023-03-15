package pl.touk.nussknacker.ui.process.deployment

import cats.data.ValidatedNel
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.subprocess.SubprocessResolver

import scala.util.{Failure, Success, Try}

class ScenarioResolver(subprocessResolver: SubprocessResolver,
                       modelConfig: ProcessingTypeDataProvider[Config],
                       classloader: ProcessingTypeDataProvider[ClassLoader]) {

  def resolveScenario(canonical: CanonicalProcess, category: Category, processingType: ProcessingType): Try[CanonicalProcess] =
    toTry(subprocessResolver.resolveSubprocesses(canonical.withoutDisabledNodes, category,
      modelConfig.forTypeUnsafe(processingType),
      classloader.forTypeUnsafe(processingType)))

  private def toTry[E, A](validated: ValidatedNel[E, A]) =
    validated.map(Success(_)).valueOr(e => Failure(new RuntimeException(e.head.toString)))

}
