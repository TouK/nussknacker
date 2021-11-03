package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.process.{Sink, _}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MethodToInvoke, Service}
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object ComponentModelData {
  val categoryMarketing = "Marketing"
  val categoryMarketingTests = "MarketingTest"
  val categoryMarketingSuper = "MarketingSuper"

  val marketingWithoutSuperCategories: List[String] = List(categoryMarketing, categoryMarketingTests).sorted
  val marketingAllCategories: List[String] = List(categoryMarketing, categoryMarketingTests, categoryMarketingSuper).sorted

  val categoryFraud = "Fraud"
  val categoryFraudTests = "FraudTest"
  val categoryFraudSuper = "FraudSuper"

  val fraudWithoutSupperCategories: List[String] = List(categoryFraud, categoryFraudTests).sorted
  val fraudAllCategories: List[String] = List(categoryFraud, categoryFraudTests, categoryFraudSuper).sorted

  val allCategories: List[String] = (marketingAllCategories ++ fraudAllCategories).sorted

  val sharedSourceId = "emptySource"
  val sharedSinkId = "sendEmail"
  val sharedEnricherId = "sharedEnricher"
}

abstract class DefaultStreamingProcessConfigCreator extends EmptyProcessConfigCreator {

  import ComponentModelData._

  protected def admin[T](value: T): WithCategories[T] = WithCategories(value, categoryMarketingSuper, categoryFraudSuper)

  protected def marketing[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, categoryMarketing).withComponentId(componentId)

  protected def marketingAndTests[T](value: T): WithCategories[T] = WithCategories(value, categoryMarketing, categoryMarketingTests)

  protected def fraud[T](value: T): WithCategories[T] = WithCategories(value, categoryFraud)

  protected def fraudAndTests[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, categoryFraud, categoryFraudTests).withComponentId(componentId)

  protected def all[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, categoryMarketing, categoryMarketingTests, categoryMarketingSuper, categoryFraud, categoryFraudTests, categoryFraudSuper).withComponentId(componentId)

  case object EmptySink extends Sink

  case object EmptySource extends Source[Map[String, String]]

  case object CustomerDataEnricher extends Service with Serializable {
    @MethodToInvoke def invoke()(implicit ec: ExecutionContext): Future[Int] = Future.apply(Random.nextInt())
  }

  case object EmptyProcessor extends Service {
    @MethodToInvoke def invoke(): Future[Unit] = Future.unit
  }

  case class EmptyCustomStreamTransformer(override val canHaveManyInputs: Boolean, override val canBeEnding: Boolean) extends CustomStreamTransformer {
    @MethodToInvoke(returnType = classOf[Void]) def invoke(): Unit = {}
  }
}

object ComponentMarketingTestConfigCreator extends DefaultStreamingProcessConfigCreator {
  import ComponentModelData._

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
    sharedSourceId -> marketing(SourceFactory.noParam(EmptySource), Some(sharedSourceId)),
    "superSource" -> admin(SourceFactory.noParam(EmptySource)),
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    sharedSinkId -> marketing(SinkFactory.noParam(EmptySink), Some(sharedSinkId)),
    "monitor" -> all(SinkFactory.noParam(EmptySink)),
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "fuseBlockService" -> marketingAndTests(EmptyProcessor),
    "customerDataEnricher" -> marketing(CustomerDataEnricher),
    sharedEnricherId -> marketing(CustomerDataEnricher, Some(sharedEnricherId)),
    "hiddenMarketingCustomerDataEnricher" -> all(CustomerDataEnricher),
  )

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "customStream" -> marketingAndTests(EmptyCustomStreamTransformer(true, false)),
    "optionalCustomStream" -> marketingAndTests(EmptyCustomStreamTransformer(false, true)),
  )
}

object ComponentFraudTestConfigCreator extends DefaultStreamingProcessConfigCreator {
  import ComponentModelData._
  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
    sharedSourceId -> all(SourceFactory.noParam(EmptySource), Some(sharedSourceId)),
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    sharedSinkId -> fraudAndTests(SinkFactory.noParam(EmptySink), Some(sharedSinkId)),
    "secondMonitor" -> all(SinkFactory.noParam(EmptySink)),
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "fuseBlockService" -> fraudAndTests(EmptyProcessor),
    "customerDataEnricher" -> fraud(CustomerDataEnricher),
    sharedEnricherId -> fraudAndTests(CustomerDataEnricher, Some(sharedEnricherId)),
    "hiddenFraudCustomerDataEnricher" -> all(CustomerDataEnricher),
  )

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "customStream" -> fraudAndTests(EmptyCustomStreamTransformer(true, false)),
    "optionalCustomStream" -> fraudAndTests(EmptyCustomStreamTransformer(false, true)),
  )
}
