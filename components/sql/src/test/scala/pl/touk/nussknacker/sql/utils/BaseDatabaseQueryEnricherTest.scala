package pl.touk.nussknacker.sql.utils

import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{FailedToDefineParameter, OutputVariableNameValue}
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.sql.service.DatabaseQueryEnricher

import scala.concurrent.ExecutionContext

trait BaseDatabaseQueryEnricherTest extends FunSuite with Matchers with BeforeAndAfterAll {

  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  implicit val contextId: ContextId = ContextId("")
  implicit val metaData: MetaData = MetaData("", StreamMetaData())
  implicit val collector: ServiceInvocationCollector = EmptyInvocationCollector.Instance
  implicit val componentUseCase: ComponentUseCase = ComponentUseCase.TestRuntime

  val jobData: JobData = JobData(MetaData("", StreamMetaData()), ProcessVersion.empty)

  val service: Lifecycle

  protected def returnType(service: DatabaseQueryEnricher, state: DatabaseQueryEnricher.TransformationState): typing.TypingResult = {
    val varName = "varName1"
    service.contextTransformation(ValidationContext.empty,
      List(OutputVariableNameValue(varName)))(NodeId("test"))(service.TransformationStep(List(("notUsed", FailedToDefineParameter)), Some(state))) match {
      case service.FinalResults(finalContext, _, _) => finalContext.apply(varName)
      case a => throw new AssertionError(s"Should not happen: $a")
    }
  }
}
