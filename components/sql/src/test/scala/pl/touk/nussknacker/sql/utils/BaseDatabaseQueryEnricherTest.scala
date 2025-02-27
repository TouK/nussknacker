package pl.touk.nussknacker.sql.utils

import cats.data.NonEmptyList
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.EmptyProcess
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{FailedToDefineParameter, OutputVariableNameValue}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.sql.service.DatabaseQueryEnricher

import scala.concurrent.ExecutionContext

trait BaseDatabaseQueryEnricherTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {

  implicit val ec: ExecutionContext                     = ExecutionContext.Implicits.global
  implicit val context: Context                         = Context("", Map.empty)
  implicit val metaData: MetaData                       = MetaData("", StreamMetaData())
  implicit val collector: ServiceInvocationCollector    = EmptyInvocationCollector.Instance
  implicit val componentUseContext: ComponentUseContext = ComponentUseContext.ScenarioTesting

  val jobData: JobData = JobData(metaData, ProcessVersion.empty.copy(processName = metaData.name))

  val service: Service

  protected def returnType(
      service: DatabaseQueryEnricher,
      state: DatabaseQueryEnricher.TransformationState
  ): typing.TypingResult = {
    val varName = "varName1"
    service.contextTransformation(ValidationContext.empty, List(OutputVariableNameValue(varName)))(NodeId("test"))(
      service.TransformationStep(
        List((ParameterName("notUsed"), FailedToDefineParameter(NonEmptyList.one(EmptyProcess)))),
        Some(state)
      )
    ) match {
      case service.FinalResults(finalContext, _, _) => finalContext.apply(varName)
      case a                                        => throw new AssertionError(s"Should not happen: $a")
    }
  }

}
