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
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData.toDetails
import pl.touk.nussknacker.ui.api.helpers.TestFactory.{MockDeploymentManager, StubSubprocessRepository}
import pl.touk.nussknacker.ui.api.helpers.{MockFetchingProcessRepository, TestFactory, TestProcessUtil, TestProcessingTypes}
import pl.touk.nussknacker.ui.config.ComponentActionConfig
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails
import pl.touk.nussknacker.ui.security.api.Permission.Read
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}

class DefaultComponentServiceSpec extends FlatSpec with Matchers with PatientScalaFutures {

  import ComponentActionConfig._
  import ComponentModelData._
  import DefaultsComponentGroupName._
  import DefaultsComponentIcon._
  import DynamicComponentProvider._
  import TestProcessingTypes._
  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.engine.api.component.ComponentType._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val ExecutionGroupName: ComponentGroupName = ComponentGroupName("execution")
  private val ResponseGroupName: ComponentGroupName = ComponentGroupName("response")
  private val HiddenGroupName: ComponentGroupName = ComponentGroupName("hidden")
  private val OverriddenGroupName: ComponentGroupName = ComponentGroupName("OverriddenGroupName")
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

  private val overrideKafkaSinkId = ComponentId.create(s"$Sink-$KafkaAvroProvidedComponentName")
  private val overrideKafkaSourceId = ComponentId.create(s"$Source-$KafkaAvroProvidedComponentName")
  private val customerDataEnricherId = ComponentId.create(customerDataEnricherName)
  private val sharedEnricherId = ComponentId.create(sharedEnricherName)
  private val customStreamId = ComponentId.create(customStreamName)
  private val sharedSourceId = ComponentId.create(sharedSourceName)
  private val sharedProvidedComponentId = ComponentId.create(SharedProvidedComponentName)

  private val streamingConfig: Config = ConfigFactory.parseString(
    s"""
      |{
      |  componentsUiConfig {
      |    $customerDataEnricherName {
      |      icon: "$OverriddenIcon"
      |      componentGroup: "$ResponseGroupName"
      |      componentId: "$customerDataEnricherId"
      |    },
      |    $Filter {
      |      icon: "$OverriddenIcon"
      |    },
      |    $hiddenMarketingCustomerDataEnricherName {
      |     componentGroup: "$HiddenGroupName"
      |    },
      |    $sharedEnricherName {
      |      icon: "$OverriddenIcon"
      |    },
      |    $SharedProvidedComponentName {
      |      componentId: $SharedProvidedComponentName
      |    },
      |    ${ComponentId(Streaming, KafkaAvroProvidedComponentName, Source)} {
      |      componentId: "$overrideKafkaSourceId"
      |    }
      |    ${ComponentId(Streaming, KafkaAvroProvidedComponentName, Sink)} {
      |      componentId: "$overrideKafkaSinkId"
      |    }
      |  }
      |
      |  componentsGroupMapping {
      |    "$SinksGroupName": "$ExecutionGroupName",
      |    "$ServicesGroupName": "$ExecutionGroupName",
      |    "$HiddenGroupName": null
      |  }
      |
      |  components {
      |    $ProviderName {
      |      categories: ["$categoryMarketingTests"]
      |    }
      |  }
      |}
      |""".stripMargin)

  private val fraudConfig: Config = ConfigFactory.parseString(
    s"""
      |{
      |  componentsUiConfig {
      |    $hiddenFraudCustomerDataEnricherName {
      |     componentGroup: "$HiddenGroupName"
      |    }
      |    $categoryFraud {
      |      icon: "$OverriddenIcon"
      |    }
      |    $Filter {
      |      icon: "$OverriddenIcon"
      |    },
      |    $sharedEnricherName {
      |      icon: "$OverriddenIcon"
      |    },
      |    $SharedProvidedComponentName {
      |      componentId: $SharedProvidedComponentName
      |    },
      |    ${ComponentId(Fraud, KafkaAvroProvidedComponentName, Source)} {
      |      componentId: "$overrideKafkaSourceId"
      |    }
      |    ${ComponentId(Fraud, KafkaAvroProvidedComponentName, Sink)} {
      |      componentId: "$overrideKafkaSinkId"
      |    }
      |  }
      |
      |  componentsGroupMapping {
      |    "$SinksGroupName": "$ExecutionGroupName",
      |    "$ServicesGroupName": "$ExecutionGroupName",
      |    "$HiddenGroupName": null
      |  }
      |
      |  components {
      |    $ProviderName {
      |      categories: ["$categoryFraudTests"]
      |    }
      |  }
      |}
      |""".stripMargin)

  private val wrongConfig: Config = ConfigFactory.parseString(
    s"""
       |{
       |  componentsUiConfig {
       |    $sharedSourceV2Name {
       |      icon: "$OverriddenIcon"
       |      componentGroup: "$ExecutionGroupName"
       |    },
       |    $SharedProvidedComponentName {
       |      componentId: "$SharedProvidedComponentName"
       |      icon: "$OverriddenIcon"
       |      componentGroup: $OverriddenGroupName
       |    }
       |  }
       |
       |  components {
       |    $ProviderName {
       |      categories: ["$categoryMarketingTests"]
       |    }
       |  }
       |}
       |""".stripMargin)

  private val categoryConfig =  ConfigFactory.parseString(
    s"""
      |{
      |  categoriesConfig: {
      |    "$categoryMarketing": "$Streaming",
      |    "$categoryMarketingTests": "$Streaming",
      |    "$categoryMarketingSuper": "$Streaming",
      |    "$categoryFraud": "$Fraud",
      |    "$categoryFraudTests": "$Fraud",
      |    "$categoryFraudSuper": "$Fraud"
      |  }
      |}
      |""".stripMargin)

  private val categoryService = new ConfigProcessCategoryService(categoryConfig)

  private def createActions(componentId: ComponentId, componentName: String, componentType: ComponentType): List[ComponentAction] =
    actionsConfig
      .filter(_.isAvailable(componentType))
      .map(_.toComponentAction(componentId, componentName))

  private object MockManagerProvider extends FlinkStreamingDeploymentManagerProvider {
    override def createDeploymentManager(modelData: ModelData, config: Config)
                                        (implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT]): DeploymentManager = new MockDeploymentManager
  }

  private def sharedComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], componentId: Option[ComponentId] = None)(implicit user: LoggedUser) = {
    val id = componentId.getOrElse(ComponentId.create(name))
    val actions = createActions(id, name, componentType)
    val usageCount = componentCount(id, user)

    val availableCategories = if (categories.isEmpty)
      categoryService.getUserCategories(user).sorted
    else
      categories.filter(user.can(_, Read)).sorted

    ComponentListElement(id, name, icon, componentType, componentGroupName, availableCategories, actions, usageCount)
  }

  private def marketingComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], componentId: Option[ComponentId] = None)(implicit user: LoggedUser) =
    createComponent(Streaming, name, icon, componentType, componentGroupName, categories, componentId)

  private def fraudComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], componentId: Option[ComponentId] = None)(implicit user: LoggedUser) =
    createComponent(Fraud, name, icon, componentType, componentGroupName, categories, componentId)

  private def createComponent(processingType: String, name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], componentId: Option[ComponentId] = None)(implicit user: LoggedUser) = {
    val id = componentId.getOrElse(ComponentId(processingType, name, componentType))
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

  private def prepareSharedComponents(implicit user: LoggedUser): List[ComponentListElement] =
    List(
      sharedComponent(sharedSourceName, SourceIcon, Source, SourcesGroupName, List(categoryMarketing) ++ fraudAllCategories),
      sharedComponent(sharedSinkName, SinkIcon, Sink, ExecutionGroupName, List(categoryMarketing) ++ fraudWithoutSupperCategories),
      sharedComponent(sharedEnricherName, OverriddenIcon, Enricher, EnrichersGroupName, List(categoryMarketing) ++ fraudWithoutSupperCategories),
      sharedComponent(SharedProvidedComponentName, ProcessorIcon, Processor, ExecutionGroupName, List(categoryMarketingTests, categoryFraudTests)),
      sharedComponent(KafkaAvroProvidedComponentName, SourceIcon, Source, SourcesGroupName, List(categoryMarketingTests, categoryFraudTests), componentId = Some(overrideKafkaSourceId)),
      sharedComponent(KafkaAvroProvidedComponentName, SinkIcon, Sink, ExecutionGroupName, List(categoryMarketingTests, categoryFraudTests), componentId = Some(overrideKafkaSinkId)),
    )

  private def prepareMarketingComponents(implicit user: LoggedUser): List[ComponentListElement] = List(
    marketingComponent(customStreamName, CustomNodeIcon, CustomNode, CustomGroupName, marketingWithoutSuperCategories, componentId = Some(customStreamId)),
    marketingComponent(customerDataEnricherName, OverriddenIcon, Enricher, ResponseGroupName, List(categoryMarketing), componentId = Some(customerDataEnricherId)),
    marketingComponent("fuseBlockService", ProcessorIcon, Processor, ExecutionGroupName, marketingWithoutSuperCategories),
    marketingComponent("monitor", SinkIcon, Sink, ExecutionGroupName, marketingAllCategories),
    marketingComponent("optionalCustomStream", CustomNodeIcon, CustomNode, OptionalEndingCustomGroupName, marketingWithoutSuperCategories),
    marketingComponent("superSource", SourceIcon, Source, SourcesGroupName, List(categoryMarketingSuper)),
    marketingComponent(SingleProvidedComponentName, ProcessorIcon, Processor, ExecutionGroupName, List(categoryMarketingTests)),
  )

  private def prepareFraudComponents(implicit user: LoggedUser): List[ComponentListElement] = List(
    fraudComponent(customStreamName, CustomNodeIcon, CustomNode, CustomGroupName, fraudWithoutSupperCategories),
    fraudComponent(customerDataEnricherName, EnricherIcon, Enricher, EnrichersGroupName, List(categoryFraud)),
    fraudComponent("fuseBlockService", ProcessorIcon, Processor, ExecutionGroupName, fraudWithoutSupperCategories),
    fraudComponent("optionalCustomStream", CustomNodeIcon, CustomNode, OptionalEndingCustomGroupName, fraudWithoutSupperCategories),
    fraudComponent("secondMonitor", SinkIcon, Sink, ExecutionGroupName, fraudAllCategories),
    fraudComponent(SingleProvidedComponentName, ProcessorIcon, Processor, ExecutionGroupName, List(categoryFraudTests)),
  )

  private val subprocessMarketingComponents: List[ComponentListElement] = marketingAllCategories.map(cat => {
    val id = ComponentId(Streaming, cat, Fragments)
    val icon = DefaultsComponentIcon.fromComponentType(Fragments)
    val actions = createActions(id, cat, Fragments)
    ComponentListElement(id, cat, icon, Fragments, FragmentsGroupName, List(cat), actions, 0)
  })

  private val subprocessFraudComponents: List[ComponentListElement] = fraudAllCategories.map(cat => {
    val id = ComponentId(Fraud, cat, Fragments)
    val icon = if (cat == categoryFraud) OverriddenIcon else DefaultsComponentIcon.fromComponentType(Fragments)
    val actions = createActions(id, cat, Fragments)
    ComponentListElement(id, cat, icon, Fragments, FragmentsGroupName, List(cat), actions, 0)
  })

  private def prepareComponents(implicit user: LoggedUser): List[ComponentListElement] =
    baseComponents ++ prepareSharedComponents ++ prepareMarketingComponents ++ prepareFraudComponents ++ subprocessMarketingComponents ++ subprocessFraudComponents

  private val subprocessFromCategories: Set[SubprocessDetails] = allCategories.map(cat => {
    val metaData = MetaData(cat, FragmentSpecificData())
    val exceptionHandler = ExceptionHandlerRef(List())
    val node = FlatNode(SubprocessInputDefinition(cat, Nil, None))
    SubprocessDetails(CanonicalProcess(metaData, exceptionHandler, List(node), Nil), cat)
  }).toSet

  private def createDisplayableProcess(id: String, processingType: String) = TestProcessUtil.toDisplayable(
    EspProcessBuilder
      .id(id)
      .exceptionHandler()
      .source("source", sharedSourceName)
      .emptySink("sink", sharedSinkName)
    , processingType = processingType
  )

  private val marketingProcess = toDetails(
    displayable = createDisplayableProcess("marketingProcess", Streaming),
    category = categoryMarketing
  )

  private val fraudProcess = toDetails(
    displayable = createDisplayableProcess("fraudProcess", Fraud),
    category = categoryFraud
  )

  private val fraudTestProcess = toDetails(
    displayable = createDisplayableProcess("fraudTestProcess", Fraud),
    category = categoryFraudTests
  )

  private val wrongCategoryProcess = toDetails(
    displayable = createDisplayableProcess("wrongCategory", Fraud),
    category = "wrongCategory"
  )

  private val archivedFraudProcess = toDetails(
    displayable = createDisplayableProcess("archivedFraudProcess", Fraud),
    isArchived = true,
    category = categoryFraud
  )

  private def componentCount(componentId: ComponentId, user: LoggedUser) = {
    val sourceId = ComponentId.create(sharedSourceName)
    val sinkId = ComponentId.create(sharedSinkName)

    componentId match {
      //Order is matter, first should be condition with more number of categories
      case _@id if id == sourceId && hasAccess(user, categoryFraud, categoryFraudTests, categoryMarketing) => 3
      case _@id if id == sinkId && hasAccess(user, categoryFraud, categoryFraudTests, categoryMarketing) => 3

      case _@id if id == sourceId && hasAccess(user, categoryFraud, categoryFraudTests) => 2
      case _@id if id == sinkId && hasAccess(user, categoryFraud, categoryFraudTests) => 2

      case _@id if id == sourceId && hasAccess(user, categoryFraudTests) => 1
      case _@id if id == sinkId && hasAccess(user, categoryFraudTests) => 1

      case _@id if id == sourceId && hasAccess(user, categoryFraud) => 1
      case _@id if id == sinkId && hasAccess(user, categoryFraud) => 1

      case _@id if id == sourceId && hasAccess(user, categoryMarketing) => 1
      case _@id if id == sinkId && hasAccess(user, categoryMarketing) => 1

      case _ => 0
    }
  }

  private def hasAccess(user: LoggedUser, categories: Category*): Boolean =
    categories.forall(cat => user.can(cat, Permission.Read))

  private val admin = TestFactory.adminUser()
  private val marketingFullUser = TestFactory.userWithCategoriesReadPermission(username = "marketingFullUser", categories = marketingWithoutSuperCategories)
  private val marketingTestsUser = TestFactory.userWithCategoriesReadPermission(username = "marketingTestsUser", categories = List(categoryMarketingTests))
  private val fraudFullUser = TestFactory.userWithCategoriesReadPermission(username = "fraudFullUser", categories = fraudWithoutSupperCategories)
  private val fraudTestsUser = TestFactory.userWithCategoriesReadPermission(username = "fraudTestsUser", categories = List(categoryFraudTests))

  it should "return components for each user" in {
    val processingTypeDataProvider = new MapBasedProcessingTypeDataProvider(Map(
      Streaming -> LocalModelData(streamingConfig, ComponentMarketingTestConfigCreator),
      Fraud -> LocalModelData(fraudConfig, ComponentFraudTestConfigCreator),
    ).map{ case (processingType, config) =>
      processingType -> ProcessingTypeData(new MockDeploymentManager, config, MockManagerProvider.typeSpecificInitialData, None, supportsSignals = false)
    })

    val processes = List(marketingProcess, fraudProcess, fraudTestProcess, wrongCategoryProcess, archivedFraudProcess)
    val stubSubprocessRepository = new StubSubprocessRepository(subprocessFromCategories)
    val fetchingProcessRepositoryMock = MockFetchingProcessRepository(processes)
    val defaultComponentService = DefaultComponentService(globalConfig, processingTypeDataProvider, fetchingProcessRepositoryMock, stubSubprocessRepository, categoryService)

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

  it should "throws exception when components are wrong configured" in {
    import WrongConfigurationAttribute._
    val processingTypeDataProvider = new MapBasedProcessingTypeDataProvider(Map(
      Streaming -> LocalModelData(streamingConfig, ComponentMarketingTestConfigCreator),
      Fraud -> LocalModelData(wrongConfig, WronglyConfiguredConfigCreator),
    ).map { case (processingType, config) =>
      processingType -> ProcessingTypeData(new MockDeploymentManager, config, MockManagerProvider.typeSpecificInitialData, None, supportsSignals = false)
    })

    val stubSubprocessRepository = new StubSubprocessRepository(Set.empty)
    val fetchingProcessRepositoryMock = MockFetchingProcessRepository(List(marketingProcess))

    val expectedWrongConfigurations = List(
      ComponentWrongConfiguration(sharedSourceId, NameAttribute, List(sharedSourceName, sharedSourceV2Name)),
      ComponentWrongConfiguration(sharedSourceId, ComponentGroupNameAttribute, List(SourcesGroupName, ExecutionGroupName)),
      //ComponentWrongConfiguration(ComponentId.create(hiddenMarketingCustomerDataEnricherName), ComponentGroupNameAttribute, List(HiddenGroupName, CustomGroupName)), //TODO: right now we don't support hidden components, see how works UIProcessObjectsFactory.prepareUIProcessObjects
      ComponentWrongConfiguration(sharedSourceId, IconAttribute, List(DefaultsComponentIcon.fromComponentType(Source), OverriddenIcon)),
      ComponentWrongConfiguration(sharedEnricherId, ComponentTypeAttribute, List(Enricher, Processor)),
      ComponentWrongConfiguration(sharedEnricherId, IconAttribute, List(OverriddenIcon, DefaultsComponentIcon.fromComponentType(Processor))),
      ComponentWrongConfiguration(sharedEnricherId, ComponentGroupNameAttribute, List(EnrichersGroupName, ServicesGroupName)),
      ComponentWrongConfiguration(ComponentId.forBaseComponent(Filter), IconAttribute, List(OverriddenIcon, DefaultsComponentIcon.fromComponentType(Filter))),
      ComponentWrongConfiguration(sharedProvidedComponentId, IconAttribute, List(DefaultsComponentIcon.fromComponentType(Processor), OverriddenIcon)),
      ComponentWrongConfiguration(sharedProvidedComponentId, ComponentGroupNameAttribute, List(ExecutionGroupName, OverriddenGroupName)),
    )

    val wrongConfigurations = intercept[ComponentConfigurationException] {
      DefaultComponentService(globalConfig, processingTypeDataProvider, fetchingProcessRepositoryMock, stubSubprocessRepository, categoryService)
    }.wrongConfigurations

    wrongConfigurations should contain allElementsOf expectedWrongConfigurations
  }
}
