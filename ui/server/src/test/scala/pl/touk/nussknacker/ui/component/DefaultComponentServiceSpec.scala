package pl.touk.nussknacker.ui.component

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeData}
import pl.touk.nussknacker.restmodel.component.{ComponentAction, ComponentListElement}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData.toDetails
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{MockDeploymentManager, StubSubprocessRepository}
import pl.touk.nussknacker.ui.api.helpers.{StubProcessRepository, TestFactory, TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.config.ComponentActionConfig
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class DefaultComponentServiceSpec extends FlatSpec with Matchers with PatientScalaFutures {

  import ComponentActionConfig._
  import ComponentModelData._
  import DefaultsComponentGroupName._
  import DefaultsComponentIcon._
  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.engine.api.component.ComponentType._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val ExecutionGroupName: ComponentGroupName = ComponentGroupName("execution")
  private val ResponseGroupName: ComponentGroupName = ComponentGroupName("response")
  private val OverriddenIcon = "OverriddenIcon.svg"

  private val usagesActionId = "usages"
  private val invokeActionId = "invoke"
  private val editActionId = "edit"
  private val filterActionId = "filter"

  private val actionsConfig = List(
    ComponentActionConfig(usagesActionId, s"Usages of $ComponentNameTemplate", s"/assets/components/actions/usages.svg", None, None),
    ComponentActionConfig(invokeActionId, s"Invoke component $ComponentNameTemplate", s"/assets/components/actions/invoke.svg", Some(s"/components/$ComponentIdTemplate/Invoke"), Some(List(Enricher, Processor))),
    ComponentActionConfig(editActionId, s"Edit component $ComponentNameTemplate", "/assets/components/actions/edit.svg", Some(s"/components/$ComponentIdTemplate/"), Some(List(CustomNode, Enricher, Processor))),
    ComponentActionConfig(filterActionId, s"Custom action $ComponentNameTemplate", "/assets/components/actions/filter.svg", Some(s"/components/$ComponentIdTemplate/filter"), Some(List(Filter))),
  )

  private val globalConfig = ConfigFactory.parseString(
    s"""
      componentActions: [
        ${actionsConfig.map { action =>
      s"""{
         | id: "${action.id}",
         | title: "${action.title}",
         | ${action.url.map(url => s"""url: "$url",""").getOrElse("")}
         | icon: "${action.icon}",
         | ${action.supportedComponentTypes.map(types => s"""supportedComponentTypes: [${types.mkString(",")}]""").getOrElse("")}
         | }""".stripMargin
          }.mkString(",\n")}
      ]
    """
  )

  private val streamingConfig: Config = ConfigFactory.parseString(
    s"""
      |{
      |  componentsUiConfig {
      |    customerDataEnricher {
      |      icon: "$OverriddenIcon"
      |      componentGroup: "response"
      |    },
      |    filter {
      |      icon: "$OverriddenIcon"
      |    },
      |    hiddenMarketingCustomerDataEnricher {
      |     componentGroup: "hidden"
      |    }
      |  }
      |
      |  componentsGroupMapping {
      |    "sinks": "execution",
      |    "services": "execution",
      |    "hidden": null
      |  }
      |
      |  components {
      |    dynamicComponent: {
      |      categories: ["$categoryMarketingTests"]
      |    }
      |  }
      |}
      |""".stripMargin)

  private val fraudConfig: Config = ConfigFactory.parseString(
    s"""
      |{
      |  componentsUiConfig {
      |    hiddenFraudCustomerDataEnricher {
      |     componentGroup: "hidden"
      |    }
      |    $categoryFraud {
      |      icon: "$OverriddenIcon"
      |    }
      |    filter {
      |      icon: "$OverriddenIcon"
      |    }
      |  }
      |
      |  componentsGroupMapping {
      |    "sinks": "execution",
      |    "hidden": null
      |  }
      |}
      |""".stripMargin)

  private val categoryConfig =  ConfigFactory.parseString(
    s"""
      |{
      |  categoriesConfig: {
      |    "$categoryMarketing": "${TestProcessingTypes.Streaming}",
      |    "$categoryMarketingTests": "${TestProcessingTypes.Streaming}",
      |    "$categoryMarketingSuper": "${TestProcessingTypes.Streaming}",
      |    "$categoryFraud": "${TestProcessingTypes.Fraud}",
      |    "$categoryFraudTests": "${TestProcessingTypes.Fraud}",
      |    "$categoryFraudSuper": "${TestProcessingTypes.Fraud}"
      |  }
      |}
      |""".stripMargin)

  private def createActions(componentId: ComponentId, componentName: String, componentType: ComponentType): List[ComponentAction] =
    actionsConfig
      .filter(_.isAvailable(componentType))
      .map(_.toComponentAction(componentId, componentName))

  private object MockManagerProvider extends FlinkStreamingDeploymentManagerProvider {
    override def createDeploymentManager(modelData: ModelData, config: Config)
                                        (implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT]): DeploymentManager = new MockDeploymentManager
  }

  private def marketingComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String])(implicit user: LoggedUser) = {
    val id = ComponentId(TestProcessingTypes.Streaming, name, componentType)
    val actions = createActions(id, name, componentType)
    val usageCount = componentCount(id, user)
    ComponentListElement(id, name, icon, componentType, componentGroupName, categories, actions, usageCount)
  }

  private def fraudComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String])(implicit user: LoggedUser) = {
    val id = ComponentId(TestProcessingTypes.Fraud, name, componentType)
    val actions = createActions(id, name, componentType)
    val usageCount = componentCount(id, user)
    ComponentListElement(id, name, icon, componentType, componentGroupName, categories, actions, usageCount)
  }

  private def baseComponent(componentType: ComponentType, icon: String, componentGroupName: ComponentGroupName, categories: List[String]) = {
    val id = ComponentId.create(componentType.toString)
    val actions = createActions(id, componentType.toString, componentType)
    ComponentListElement(id, componentType.toString, icon, componentType, componentGroupName, categories, actions, 0)
  }

  private val baseComponents: List[ComponentListElement] =
    List(
      baseComponent(Filter, OverriddenIcon, BaseGroupName, allCategories),
      baseComponent(Split, SplitIcon, BaseGroupName, allCategories),
      baseComponent(Switch, SwitchIcon, BaseGroupName, allCategories),
      baseComponent(Variable, VariableIcon, BaseGroupName, allCategories),
      baseComponent(MapVariable, MapVariableIcon, BaseGroupName, allCategories),
    )

  private def prepareMarketingComponents(implicit user: LoggedUser): List[ComponentListElement] = List(
    marketingComponent("customStream", CustomNodeIcon, CustomNode, CustomGroupName, marketingWithoutSuperCategories),
    marketingComponent("customerDataEnricher", OverriddenIcon, Enricher, ResponseGroupName, List(categoryMarketing)),
    marketingComponent(DynamicProvidedComponent.Name, ProcessorIcon, Processor, ExecutionGroupName, List(categoryMarketingTests)),
    marketingComponent(sharedSourceId, SourceIcon, Source, SourcesGroupName, List(categoryMarketing)),
    marketingComponent("fuseBlockService", ProcessorIcon, Processor, ExecutionGroupName, marketingWithoutSuperCategories),
    marketingComponent("monitor", SinkIcon, Sink, ExecutionGroupName, marketingAllCategories),
    marketingComponent("optionalCustomStream", CustomNodeIcon, CustomNode, OptionalEndingCustomGroupName, marketingWithoutSuperCategories),
    marketingComponent(sharedSinkId, SinkIcon, Sink, ExecutionGroupName, List(categoryMarketing)),
    marketingComponent("superSource", SourceIcon, Source, SourcesGroupName, List(categoryMarketingSuper)),
  )

  private def prepareFraudComponents(implicit user: LoggedUser): List[ComponentListElement] = List(
    fraudComponent("customStream", CustomNodeIcon, CustomNode, CustomGroupName, fraudWithoutSupperCategories),
    fraudComponent("customerDataEnricher", EnricherIcon, Enricher, EnrichersGroupName, List(categoryFraud)),
    fraudComponent(sharedSourceId, SourceIcon, Source, SourcesGroupName, fraudAllCategories),
    fraudComponent("fuseBlockService", ProcessorIcon, Processor, ServicesGroupName, fraudWithoutSupperCategories),
    fraudComponent("optionalCustomStream", CustomNodeIcon, CustomNode, OptionalEndingCustomGroupName, fraudWithoutSupperCategories),
    fraudComponent("secondMonitor", SinkIcon, Sink, ExecutionGroupName, fraudAllCategories),
    fraudComponent(sharedSinkId, SinkIcon, Sink, ExecutionGroupName, fraudWithoutSupperCategories),
  )

  private val subprocessMarketingComponents: List[ComponentListElement] = marketingAllCategories.map(cat => {
    val id = ComponentId(TestProcessingTypes.Streaming, cat, Fragments)
    val icon = DefaultsComponentIcon.fromComponentType(Fragments)
    val actions = createActions(id, cat, Fragments)
    ComponentListElement(id, cat, icon, Fragments, FragmentsGroupName, List(cat), actions, 0)
  })

  private val subprocessFraudComponents: List[ComponentListElement] = fraudAllCategories.map(cat => {
    val id = ComponentId(TestProcessingTypes.Fraud, cat, Fragments)
    val icon = if (cat == categoryFraud) OverriddenIcon else DefaultsComponentIcon.fromComponentType(Fragments)
    val actions = createActions(id, cat, Fragments)
    ComponentListElement(id, cat, icon, Fragments, FragmentsGroupName, List(cat), actions, 0)
  })

  private def prepareComponents(implicit user: LoggedUser): List[ComponentListElement] = (
    baseComponents ++ prepareMarketingComponents ++ prepareFraudComponents ++ subprocessMarketingComponents ++ subprocessFraudComponents
  )

  private val subprocessFromCategories: Set[SubprocessDetails] = allCategories.map(cat => {
    val metaData = MetaData(cat, FragmentSpecificData())
    val exceptionHandler = ExceptionHandlerRef(List())
    val node = FlatNode(SubprocessInputDefinition(cat, Nil, None))
    SubprocessDetails(CanonicalProcess(metaData, exceptionHandler, List(node), Nil), cat)
  }).toSet

  private val marketingProcess = toDetails(TestProcessUtil.toDisplayable(
    EspProcessBuilder
      .id("marketingProcess")
      .exceptionHandler()
      .source("source", sharedSourceId)
      .emptySink("sink", sharedSinkId)
  ), category = categoryMarketing)

  private val fraudProcess = toDetails(
    TestProcessUtil.toDisplayable(
      EspProcessBuilder
      .id("fraudProcess")
      .exceptionHandler()
      .source("source", sharedSourceId)
      .emptySink("sink", sharedSinkId),
      TestProcessingTypes.Fraud
    ), category = categoryFraud
  )

  private val fraudTestProcess = toDetails(
    TestProcessUtil.toDisplayable(
      EspProcessBuilder
        .id("fraudTestProcess")
        .exceptionHandler()
        .source("source", sharedSourceId)
        .emptySink("sink", sharedSinkId),
      TestProcessingTypes.Fraud
    ), category = categoryFraudTests
  )

  private val noneExistProcess = toDetails(
    TestProcessUtil.toDisplayable(
      EspProcessBuilder
        .id("noneExist")
        .exceptionHandler()
        .source("source", sharedSourceId)
        .emptySink("sink", sharedSinkId),
      TestProcessingTypes.Fraud
    ), category = "noneExist"
  )

  private val archivedFraudProcess = toDetails(
    TestProcessUtil.toDisplayable(
      EspProcessBuilder
        .id("archivedFraudProcess")
        .exceptionHandler()
        .source("source", sharedSourceId)
        .emptySink("sink", sharedSinkId),
      TestProcessingTypes.Fraud
    ), isArchived = true, category = categoryFraud
  )

  private def componentCount(id: ComponentId, user: LoggedUser) = {
    val streamingSink = s"${TestProcessingTypes.Streaming}-$sharedSinkId".toLowerCase
    val streamingSource = s"${TestProcessingTypes.Streaming}-$sharedSourceId".toLowerCase
    val fraudSink = s"${TestProcessingTypes.Fraud}-$sharedSinkId".toLowerCase
    val fraudSource = s"${TestProcessingTypes.Fraud}-$sharedSourceId".toLowerCase

    def hasAccess(user: LoggedUser, categories: Category*): Boolean = categories.forall(cat => user.can(cat, Permission.Read))

    id match {
      case _@ComponentId(id) if id == streamingSink && hasAccess(user, categoryMarketing) => 1
      case _@ComponentId(id) if id == streamingSource && hasAccess(user, categoryMarketing) => 1

      //Order is matter, first should be condition with more number of categories
      case _@ComponentId(id) if id == fraudSink && hasAccess(user, categoryFraud, categoryFraudTests) => 2
      case _@ComponentId(id) if id == fraudSource && hasAccess(user, categoryFraud, categoryFraudTests) => 2

      case _@ComponentId(id) if id == fraudSink && hasAccess(user, categoryFraud) => 1
      case _@ComponentId(id) if id == fraudSource && hasAccess(user, categoryFraud) => 1

      case _@ComponentId(id) if id == fraudSink && hasAccess(user, categoryFraudTests) => 1
      case _@ComponentId(id) if id == fraudSource && hasAccess(user, categoryFraudTests) => 1

      case _ => 0
    }
  }

  it should "return components for each user" in {
    val processingTypeDataProvider = new MapBasedProcessingTypeDataProvider(Map(
      TestProcessingTypes.Streaming -> LocalModelData(streamingConfig, ComponentMarketingTestConfigCreator),
      TestProcessingTypes.Fraud -> LocalModelData(fraudConfig, ComponentFraudTestConfigCreator),
    ).map{ case (processingType, config) =>
      processingType -> ProcessingTypeData(new MockDeploymentManager, config, MockManagerProvider.typeSpecificDataInitializer, None, supportsSignals = false)
    })

    val processes = List(marketingProcess, fraudProcess, fraudTestProcess, noneExistProcess, archivedFraudProcess)
    val stubSubprocessRepository = new StubSubprocessRepository(subprocessFromCategories)
    val stubProcessRepository = new StubProcessRepository[DisplayableProcess](processes)
    val categoryService = new ConfigProcessCategoryService(categoryConfig)
    val defaultComponentService = new DefaultComponentService(globalConfig, processingTypeDataProvider, stubProcessRepository, stubSubprocessRepository, categoryService)

    val admin = TestFactory.adminUser()
    val marketingFullUser = TestFactory.user(permissions = preparePermissions(marketingWithoutSuperCategories))
    val marketingTestsUser = TestFactory.user(permissions = preparePermissions(List(categoryMarketingTests)))
    val fraudFullUser = TestFactory.user(permissions = preparePermissions(fraudWithoutSupperCategories))
    val fraudTestsUser = TestFactory.user(permissions = preparePermissions(List(categoryFraudTests)))

    def filterUserComponents(user: LoggedUser, categories: List[String]): List[ComponentListElement] =
      prepareComponents(user)
        .map(c => c -> categories.intersect(c.categories))
        .filter(seq => seq._2.nonEmpty)
        .map(seq => seq._1.copy(categories = seq._2))

    val adminComponents = prepareComponents(admin)
    val marketingFullComponents = filterUserComponents(marketingFullUser, marketingWithoutSuperCategories)
    val marketingTestsComponents = filterUserComponents(marketingTestsUser, List(categoryMarketingTests))
    val fraudFullComponents = filterUserComponents(fraudFullUser, fraudWithoutSupperCategories)
    val fraudTestsComponents = filterUserComponents(fraudTestsUser, List(categoryFraudTests))

    val testingData = Table(
      ("user", "expectedComponents", "possibleCategories"),
      (admin, adminComponents, allCategories),
      (marketingFullUser, marketingFullComponents, marketingWithoutSuperCategories),
      (marketingTestsUser, marketingTestsComponents, List(categoryMarketingTests)),
      (fraudFullUser, fraudFullComponents, fraudWithoutSupperCategories),
      (fraudTestsUser, fraudTestsComponents, List(categoryFraudTests)),
    )

    forAll(testingData) { (user: LoggedUser, expectedComponents: List[ComponentListElement], possibleCategories: List[String]) =>
      val components = defaultComponentService.getComponentsList(user).futureValue
      //we don't do exact matching, to avoid handling autoLoaded components here
      components should contain allElementsOf expectedComponents

      //Components should contain only user categories
      val componentsCategories = components.flatMap(_.categories).distinct.sorted
      componentsCategories.diff(possibleCategories).isEmpty shouldBe true

      components.foreach(comp => {
        //See actionsConfig
        val availableActionsId = comp.componentType match {
          case Processor | Enricher => List(usagesActionId,invokeActionId, editActionId)
          case CustomNode => List(usagesActionId, editActionId)
          case Filter => List(usagesActionId, filterActionId)
          case _ => List(usagesActionId)
        }
        comp.actions.map(_.id) shouldBe availableActionsId

        comp.actions.foreach(action => {
          action.title should include (comp.name)
          action.url.foreach(u => u should include (comp.id.value))
        })
      })
    }
  }

  private def preparePermissions(categories: List[String]) =
    categories.map(c => c -> Set(Permission.Read)).toMap
}
