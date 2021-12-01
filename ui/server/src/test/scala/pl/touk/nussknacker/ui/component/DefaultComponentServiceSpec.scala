package pl.touk.nussknacker.ui.component

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.component.ComponentType._
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeData}
import pl.touk.nussknacker.restmodel.component.{ComponentLink, ComponentListElement, ComponentUsagesInScenario}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.test.PatientScalaFutures
import pl.touk.nussknacker.ui.api.helpers.TestFactory.MockDeploymentManager
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes._
import pl.touk.nussknacker.ui.api.helpers.{MockFetchingProcessRepository, TestFactory}
import pl.touk.nussknacker.ui.component.ComponentModelData._
import pl.touk.nussknacker.ui.component.ComponentTestProcessData._
import pl.touk.nussknacker.ui.component.DefaultsComponentGroupName._
import pl.touk.nussknacker.ui.component.DefaultsComponentIcon._
import pl.touk.nussknacker.ui.component.DynamicComponentProvider._
import pl.touk.nussknacker.ui.config.ComponentLinkConfig
import pl.touk.nussknacker.ui.config.ComponentLinkConfig._
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.{ConfigProcessCategoryService, DBProcessService, ProcessCategoryService}
import pl.touk.nussknacker.ui.security.api.Permission.Read
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import sttp.client.{NothingT, SttpBackend}

import java.time.Duration
import scala.concurrent.{ExecutionContext, Future}

class DefaultComponentServiceSpec extends FlatSpec with Matchers with PatientScalaFutures {

  import org.scalatest.prop.TableDrivenPropertyChecks._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val executionGroupName: ComponentGroupName = ComponentGroupName("execution")
  private val responseGroupName: ComponentGroupName = ComponentGroupName("response")
  private val hiddenGroupName: ComponentGroupName = ComponentGroupName("hidden")
  private val overriddenGroupName: ComponentGroupName = ComponentGroupName("OverriddenGroupName")
  private val overriddenIcon = "OverriddenIcon.svg"

  private val usagesLinkId = "usages"
  private val invokeLinkId = "invoke"
  private val editLinkId = "edit"
  private val filterLinkId = "filter"

  private val linkConfigs = List(
    ComponentLinkConfig.create(usagesLinkId, s"Usages of $ComponentNameTemplate", s"/assets/components/links/usages.svg", s"https://list-of-usages.com/$ComponentIdTemplate/", None),
    ComponentLinkConfig.create(invokeLinkId, s"Invoke component $ComponentNameTemplate", s"/assets/components/links/invoke.svg", s"https://components.com/$ComponentIdTemplate/Invoke", Some(List(Enricher, Processor))),
    ComponentLinkConfig.create(editLinkId, s"Edit component $ComponentNameTemplate", "/assets/components/links/edit.svg", s"https://components.com/$ComponentIdTemplate/", Some(List(CustomNode, Enricher, Processor))),
    ComponentLinkConfig.create(filterLinkId, s"Custom link $ComponentNameTemplate", "https://other-domain.com/assets/components/links/filter.svg", s"https://components.com/$ComponentIdTemplate/filter", Some(List(Filter))),
  )

  private val globalConfig = ConfigFactory.parseString(
    s"""
      componentLinks: [
        ${linkConfigs.map { link =>
      s"""{
         | id: "${link.id}",
         | title: "${link.title}",
         | url: "${link.url}",
         | icon: "${link.icon}",
         | ${link.supportedComponentTypes.map(types => s"""supportedComponentTypes: [${types.mkString(",")}]""").getOrElse("")}
         | }""".stripMargin
          }.mkString(",\n")}
      ]
    """
  )

  private val overrideKafkaSinkComponentId = ComponentId(s"$Sink-$KafkaAvroProvidedComponentName")
  private val overrideKafkaSourceComponentId = ComponentId(s"$Source-$KafkaAvroProvidedComponentName")
  private val customerDataEnricherComponentId = ComponentId(CustomerDataEnricherName)
  private val sharedEnricherComponentId = ComponentId(SharedEnricherName)
  private val customStreamComponentId = ComponentId(CustomStreamName)
  private val sharedSourceComponentId = ComponentId(SharedSourceName)
  private val sharedProvidedComponentId = ComponentId(SharedProvidedComponentName)

  private val streamingConfig: Config = ConfigFactory.parseString(
    s"""
      |{
      |  componentsUiConfig {
      |    $CustomerDataEnricherName {
      |      icon: "$overriddenIcon"
      |      componentGroup: "$responseGroupName"
      |      componentId: "$customerDataEnricherComponentId"
      |    },
      |    $Filter {
      |      icon: "$overriddenIcon"
      |    },
      |    $HiddenMarketingCustomerDataEnricherName {
      |     componentGroup: "$hiddenGroupName"
      |    },
      |    $SharedEnricherName {
      |      icon: "$overriddenIcon"
      |    },
      |    $SharedProvidedComponentName {
      |      componentId: $SharedProvidedComponentName
      |    },
      |    ${cid(Streaming, KafkaAvroProvidedComponentName, Source)} {
      |      componentId: "$overrideKafkaSourceComponentId"
      |    }
      |    ${cid(Streaming, KafkaAvroProvidedComponentName, Sink)} {
      |      componentId: "$overrideKafkaSinkComponentId"
      |    }
      |  }
      |
      |  componentsGroupMapping {
      |    "$SinksGroupName": "$executionGroupName",
      |    "$ServicesGroupName": "$executionGroupName",
      |    "$hiddenGroupName": null
      |  }
      |
      |  components {
      |    $ProviderName {
      |      categories: ["$CategoryMarketingTests"]
      |    }
      |  }
      |}
      |""".stripMargin)

  private val fraudConfig: Config = ConfigFactory.parseString(
    s"""
      |{
      |  componentsUiConfig {
      |    $HiddenFraudCustomerDataEnricherName {
      |     componentGroup: "$hiddenGroupName"
      |    }
      |    $CategoryFraud {
      |      icon: "$overriddenIcon"
      |    }
      |    $Filter {
      |      icon: "$overriddenIcon"
      |    },
      |    $SharedEnricherName {
      |      icon: "$overriddenIcon"
      |    },
      |    $SharedProvidedComponentName {
      |      componentId: $SharedProvidedComponentName
      |    },
      |    ${cid(Fraud, KafkaAvroProvidedComponentName, Source)} {
      |      componentId: "$overrideKafkaSourceComponentId"
      |    }
      |    ${cid(Fraud, KafkaAvroProvidedComponentName, Sink)} {
      |      componentId: "$overrideKafkaSinkComponentId"
      |    }
      |  }
      |
      |  componentsGroupMapping {
      |    "$SinksGroupName": "$executionGroupName",
      |    "$ServicesGroupName": "$executionGroupName",
      |    "$hiddenGroupName": null
      |  }
      |
      |  components {
      |    $ProviderName {
      |      categories: ["$CategoryFraudTests"]
      |    }
      |  }
      |}
      |""".stripMargin)

  private val wrongConfig: Config = ConfigFactory.parseString(
    s"""
       |{
       |  componentsUiConfig {
       |    $SharedSourceV2Name {
       |      icon: "$overriddenIcon"
       |      componentGroup: "$executionGroupName"
       |    },
       |    $SharedProvidedComponentName {
       |      componentId: "$SharedProvidedComponentName"
       |      icon: "$overriddenIcon"
       |      componentGroup: $overriddenGroupName
       |    }
       |  }
       |
       |  components {
       |    $ProviderName {
       |      categories: ["$CategoryMarketingTests"]
       |    }
       |  }
       |}
       |""".stripMargin)

  private val categoryConfig =  ConfigFactory.parseString(
    s"""
      |{
      |  categoriesConfig: {
      |    "$CategoryMarketing": "$Streaming",
      |    "$CategoryMarketingTests": "$Streaming",
      |    "$CategoryMarketingSuper": "$Streaming",
      |    "$CategoryFraud": "$Fraud",
      |    "$CategoryFraudTests": "$Fraud",
      |    "$CategoryFraudSuper": "$Fraud"
      |  }
      |}
      |""".stripMargin)

  private val categoryService = new ConfigProcessCategoryService(categoryConfig)

  private object MockManagerProvider extends FlinkStreamingDeploymentManagerProvider {
    override def createDeploymentManager(modelData: ModelData, config: Config)(implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT]): DeploymentManager =
      new MockDeploymentManager
  }

  private val baseComponents: List[ComponentListElement] =
    List(
      baseComponent(Filter, overriddenIcon, BaseGroupName, AllCategories),
      baseComponent(Split, SplitIcon, BaseGroupName, AllCategories),
      baseComponent(Switch, SwitchIcon, BaseGroupName, AllCategories),
      baseComponent(Variable, VariableIcon, BaseGroupName, AllCategories),
      baseComponent(MapVariable, MapVariableIcon, BaseGroupName, AllCategories),
    )

  private def prepareSharedComponents(implicit user: LoggedUser): List[ComponentListElement] =
    List(
      sharedComponent(SharedSourceName, SourceIcon, Source, SourcesGroupName, List(CategoryMarketing) ++ FraudAllCategories),
      sharedComponent(SharedSinkName, SinkIcon, Sink, executionGroupName, List(CategoryMarketing) ++ FraudWithoutSupperCategories),
      sharedComponent(SharedEnricherName, overriddenIcon, Enricher, EnrichersGroupName, List(CategoryMarketing) ++ FraudWithoutSupperCategories),
      sharedComponent(SharedProvidedComponentName, ProcessorIcon, Processor, executionGroupName, List(CategoryMarketingTests, CategoryFraudTests)),
      sharedComponent(KafkaAvroProvidedComponentName, SourceIcon, Source, SourcesGroupName, List(CategoryMarketingTests, CategoryFraudTests), componentId = Some(overrideKafkaSourceComponentId)),
      sharedComponent(KafkaAvroProvidedComponentName, SinkIcon, Sink, executionGroupName, List(CategoryMarketingTests, CategoryFraudTests), componentId = Some(overrideKafkaSinkComponentId)),
    )

  private def prepareMarketingComponents(implicit user: LoggedUser): List[ComponentListElement] = List(
    marketingComponent(CustomStreamName, CustomNodeIcon, CustomNode, CustomGroupName, MarketingWithoutSuperCategories, componentId = Some(customStreamComponentId)),
    marketingComponent(CustomerDataEnricherName, overriddenIcon, Enricher, responseGroupName, List(CategoryMarketing), componentId = Some(customerDataEnricherComponentId)),
    marketingComponent(FuseBlockServiceName, ProcessorIcon, Processor, executionGroupName, MarketingWithoutSuperCategories),
    marketingComponent(MonitorName, SinkIcon, Sink, executionGroupName, MarketingAllCategories),
    marketingComponent(OptionalCustomStreamName, CustomNodeIcon, CustomNode, OptionalEndingCustomGroupName, MarketingWithoutSuperCategories),
    marketingComponent(SuperMarketingSourceName, SourceIcon, Source, SourcesGroupName, List(CategoryMarketingSuper)),
    marketingComponent(SingleProvidedComponentName, ProcessorIcon, Processor, executionGroupName, List(CategoryMarketingTests)),
  )

  private def prepareFraudComponents(implicit user: LoggedUser): List[ComponentListElement] = List(
    fraudComponent(CustomStreamName, CustomNodeIcon, CustomNode, CustomGroupName, FraudWithoutSupperCategories),
    fraudComponent(CustomerDataEnricherName, EnricherIcon, Enricher, EnrichersGroupName, List(CategoryFraud)),
    fraudComponent(FuseBlockServiceName, ProcessorIcon, Processor, executionGroupName, FraudWithoutSupperCategories),
    fraudComponent(OptionalCustomStreamName, CustomNodeIcon, CustomNode, OptionalEndingCustomGroupName, FraudWithoutSupperCategories),
    fraudComponent(SecondMonitorName, SinkIcon, Sink, executionGroupName, FraudAllCategories),
    fraudComponent(SingleProvidedComponentName, ProcessorIcon, Processor, executionGroupName, List(CategoryFraudTests)),
    fraudComponent(NotSharedSourceName, SourceIcon, Source, SourcesGroupName, FraudAllCategories),
    fraudComponent(FraudSinkName, SinkIcon, Sink, executionGroupName, FraudAllCategories),
  )

  private def sharedComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], componentId: Option[ComponentId] = None)(implicit user: LoggedUser) = {
    val id = componentId.getOrElse(ComponentId(name))
    val links = createLinks(id, name, componentType)
    val usageCount = componentCount(id, user)

    val availableCategories = if (categories.isEmpty)
      categoryService.getUserCategories(user).sorted
    else
      categories.filter(user.can(_, Read)).sorted

    ComponentListElement(id, name, icon, componentType, componentGroupName, availableCategories, links, usageCount)
  }

  private val subprocessMarketingComponents: List[ComponentListElement] = MarketingAllCategories.map(cat => {
    val componentId = cid(Streaming, cat, Fragments)
    val icon = DefaultsComponentIcon.fromComponentType(Fragments)
    val links = createLinks(componentId, cat, Fragments)
    ComponentListElement(componentId, cat, icon, Fragments, FragmentsGroupName, List(cat), links, 0)
  })

  private val subprocessFraudComponents: List[ComponentListElement] = FraudAllCategories.map(cat => {
    val componentId = cid(Fraud, cat, Fragments)
    val icon = if (cat == CategoryFraud) overriddenIcon else DefaultsComponentIcon.fromComponentType(Fragments)
    val links = createLinks(componentId, cat, Fragments)
    ComponentListElement(componentId, cat, icon, Fragments, FragmentsGroupName, List(cat), links, 0)
  })

  private def prepareComponents(implicit user: LoggedUser): List[ComponentListElement] =
    baseComponents ++ prepareSharedComponents ++ prepareMarketingComponents ++ prepareFraudComponents ++ subprocessMarketingComponents ++ subprocessFraudComponents

  private val subprocessFromCategories = AllCategories.flatMap(cat => categoryService.getTypeForCategory(cat).map(processingType =>
    createSubProcess(cat, category = cat, processingType = processingType)
  )).toSet

  private def marketingComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], componentId: Option[ComponentId] = None)(implicit user: LoggedUser) =
    createComponent(Streaming, name, icon, componentType, componentGroupName, categories, componentId)

  private def fraudComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], componentId: Option[ComponentId] = None)(implicit user: LoggedUser) =
    createComponent(Fraud, name, icon, componentType, componentGroupName, categories, componentId)

  private def createComponent(processingType: String, name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], componentId: Option[ComponentId] = None)(implicit user: LoggedUser) = {
    val compId = componentId.getOrElse(cid(processingType, name, componentType))
    val links = createLinks(compId, name, componentType)
    val usageCount = componentCount(compId, user)
    ComponentListElement(compId, name, icon, componentType, componentGroupName, categories, links, usageCount)
  }

  private def baseComponent(componentType: ComponentType, icon: String, componentGroupName: ComponentGroupName, categories: List[String]) = {
    val componentId = bid(componentType)
    val links = createLinks(componentId, componentType.toString, componentType)
    ComponentListElement(componentId, componentType.toString, icon, componentType, componentGroupName, categories, links, 0)
  }

  private def createLinks(componentId: ComponentId, componentName: String, componentType: ComponentType): List[ComponentLink] =
    linkConfigs
      .filter(_.isAvailable(componentType))
      .map(_.toComponentLink(componentId, componentName))

  private def componentCount(componentId: ComponentId, user: LoggedUser) = {
    val sourceComponentId = ComponentId(SharedSourceName)
    val sinkComponentId = ComponentId(SharedSinkName)

    componentId match {
      //Order is matter, first should be condition with more number of categories
      case _@id if id == sourceComponentId && hasAccess(user, CategoryFraud, CategoryFraudTests, CategoryMarketing) => 3
      case _@id if id == sinkComponentId && hasAccess(user, CategoryFraud, CategoryFraudTests, CategoryMarketing) => 3

      case _@id if id == sourceComponentId && hasAccess(user, CategoryFraud, CategoryFraudTests) => 2
      case _@id if id == sinkComponentId && hasAccess(user, CategoryFraud, CategoryFraudTests) => 2

      case _@id if id == sourceComponentId && hasAccess(user, CategoryFraudTests) => 1
      case _@id if id == sinkComponentId && hasAccess(user, CategoryFraudTests) => 1

      case _@id if id == sourceComponentId && hasAccess(user, CategoryFraud) => 1
      case _@id if id == sinkComponentId && hasAccess(user, CategoryFraud) => 1

      case _@id if id == sourceComponentId && hasAccess(user, CategoryMarketing) => 1
      case _@id if id == sinkComponentId && hasAccess(user, CategoryMarketing) => 1

      case _ => 0
    }
  }

  private def hasAccess(user: LoggedUser, categories: Category*): Boolean =
    categories.forall(cat => user.can(cat, Permission.Read))

  private val admin = TestFactory.adminUser()
  private val marketingFullUser = TestFactory.userWithCategoriesReadPermission(username = "marketingFullUser", categories = MarketingWithoutSuperCategories)
  private val marketingTestsUser = TestFactory.userWithCategoriesReadPermission(username = "marketingTestsUser", categories = List(CategoryMarketingTests))
  private val fraudFullUser = TestFactory.userWithCategoriesReadPermission(username = "fraudFullUser", categories = FraudWithoutSupperCategories)
  private val fraudTestsUser = TestFactory.userWithCategoriesReadPermission(username = "fraudTestsUser", categories = List(CategoryFraudTests))

  private val processingTypeDataProvider = new MapBasedProcessingTypeDataProvider(Map(
    Streaming -> LocalModelData(streamingConfig, ComponentMarketingTestConfigCreator),
    Fraud -> LocalModelData(fraudConfig, ComponentFraudTestConfigCreator),
  ).map{ case (processingType, config) =>
    processingType -> ProcessingTypeData(new MockDeploymentManager, config, MockManagerProvider.typeSpecificInitialData, None, supportsSignals = false)
  })

  it should "return components for each user" in {
    val processes = List(MarketingProcess, FraudProcess, FraudTestProcess, WrongCategoryProcess, ArchivedFraudProcess)
    val processService = createDbProcessService(categoryService, processes ++ subprocessFromCategories.toList)
    val defaultComponentService = DefaultComponentService(globalConfig, processingTypeDataProvider, processService, categoryService)

    def filterUserComponents(user: LoggedUser, categories: List[String]): List[ComponentListElement] =
      prepareComponents(user)
        .map(c => c -> categories.intersect(c.categories))
        .filter(seq => seq._2.nonEmpty)
        .map(seq => seq._1.copy(categories = seq._2))

    val adminComponents = prepareComponents(admin)
    val marketingFullComponents = filterUserComponents(marketingFullUser, MarketingWithoutSuperCategories)
    val marketingTestsComponents = filterUserComponents(marketingTestsUser, List(CategoryMarketingTests))
    val fraudFullComponents = filterUserComponents(fraudFullUser, FraudWithoutSupperCategories)
    val fraudTestsComponents = filterUserComponents(fraudTestsUser, List(CategoryFraudTests))

    val testingData = Table(
      ("user", "expectedComponents", "possibleCategories"),
      (admin, adminComponents, AllCategories),
      (marketingFullUser, marketingFullComponents, MarketingWithoutSuperCategories),
      (marketingTestsUser, marketingTestsComponents, List(CategoryMarketingTests)),
      (fraudFullUser, fraudFullComponents, FraudWithoutSupperCategories),
      (fraudTestsUser, fraudTestsComponents, List(CategoryFraudTests)),
    )

    forAll(testingData) { (user: LoggedUser, expectedComponents: List[ComponentListElement], possibleCategories: List[String]) =>
      val components = defaultComponentService.getComponentsList(user).futureValue

      //we don't do exact matching, to avoid handling autoLoaded components here
      components should contain allElementsOf expectedComponents

      //Components should contain only user categories
      val componentsCategories = components.flatMap(_.categories).distinct.sorted
      componentsCategories.diff(possibleCategories).isEmpty shouldBe true

      components.foreach(comp => {
        //See linksConfig
        val availableLinksId = comp.componentType match {
          case Processor | Enricher => List(usagesLinkId,invokeLinkId, editLinkId)
          case CustomNode => List(usagesLinkId, editLinkId)
          case Filter => List(usagesLinkId, filterLinkId)
          case _ => List(usagesLinkId)
        }
        comp.links.map(_.id) shouldBe availableLinksId

        comp.links.foreach(link => {
          link.title should include (comp.name)
          link.url.toString should include (comp.id.value)
        })
      })
    }
  }

  it should "throws exception when components are wrong configured" in {
    import WrongConfigurationAttribute._
    val badProcessingTypeDataProvider = new MapBasedProcessingTypeDataProvider(Map(
      Streaming -> LocalModelData(streamingConfig, ComponentMarketingTestConfigCreator),
      Fraud -> LocalModelData(wrongConfig, WronglyConfiguredConfigCreator),
    ).map { case (processingType, config) =>
      processingType -> ProcessingTypeData(new MockDeploymentManager, config, MockManagerProvider.typeSpecificInitialData, None, supportsSignals = false)
    })

    val processService = createDbProcessService(categoryService, List(MarketingProcess))

    val expectedWrongConfigurations = List(
      ComponentWrongConfiguration(sharedSourceComponentId, NameAttribute, List(SharedSourceName, SharedSourceV2Name)),
      ComponentWrongConfiguration(sharedSourceComponentId, ComponentGroupNameAttribute, List(SourcesGroupName, executionGroupName)),
      //ComponentWrongConfiguration(ComponentId.create(hiddenMarketingCustomerDataEnricherName), ComponentGroupNameAttribute, List(HiddenGroupName, CustomGroupName)), //TODO: right now we don't support hidden components, see how works UIProcessObjectsFactory.prepareUIProcessObjects
      ComponentWrongConfiguration(sharedSourceComponentId, IconAttribute, List(DefaultsComponentIcon.fromComponentType(Source), overriddenIcon)),
      ComponentWrongConfiguration(sharedEnricherComponentId, ComponentTypeAttribute, List(Enricher, Processor)),
      ComponentWrongConfiguration(sharedEnricherComponentId, IconAttribute, List(overriddenIcon, DefaultsComponentIcon.fromComponentType(Processor))),
      ComponentWrongConfiguration(sharedEnricherComponentId, ComponentGroupNameAttribute, List(EnrichersGroupName, ServicesGroupName)),
      ComponentWrongConfiguration(bid(Filter), IconAttribute, List(overriddenIcon, DefaultsComponentIcon.fromComponentType(Filter))),
      ComponentWrongConfiguration(sharedProvidedComponentId, IconAttribute, List(DefaultsComponentIcon.fromComponentType(Processor), overriddenIcon)),
      ComponentWrongConfiguration(sharedProvidedComponentId, ComponentGroupNameAttribute, List(executionGroupName, overriddenGroupName)),
    )

    val wrongConfigurations = intercept[ComponentConfigurationException] {
      DefaultComponentService(globalConfig, badProcessingTypeDataProvider, processService, categoryService)
    }.wrongConfigurations

    wrongConfigurations should contain allElementsOf expectedWrongConfigurations
  }

  it should "return components usage" in {
    val processes = List(
      MarketingProcess, FraudProcess, FraudProcessWithNotSharedSource, CanceledFraudProcessWith2Enrichers,
      DeployedFraudProcessWith2Filters, ArchivedFraudProcess, FraudProcessWithSubprocess, FraudSubprocess
    )

    val fraudNotSharedSourceComponentId = cid(Fraud, NotSharedSourceName, Source)
    val fraudCustomerDataEnricherComponentId = cid(Fraud, CustomerDataEnricherName, Enricher)
    val sharedSourceComponentId = ComponentId(SharedSourceName) //it's shared id - merged at configs file
    val subprocessComponentId = cid(Fraud, FraudSubprocessName, Fragments)
    val filterComponentId = bid(Filter)

    val processService = createDbProcessService(categoryService, processes)
    val defaultComponentService = DefaultComponentService(globalConfig, processingTypeDataProvider, processService, categoryService)

    val testingData = Table(
      ("user", "componentId", "expected"),
      (admin, subprocessComponentId, List((FraudProcessWithSubprocess, List(FraudSubprocessName)))),
      (admin, sharedSourceComponentId, List(
        (ArchivedFraudProcess, List(SecondSourceName)), (CanceledFraudProcessWith2Enrichers, List(DefaultSourceName)),
        (DeployedFraudProcessWith2Filters, List(DefaultSourceName)), (FraudProcess, List(DefaultSourceName)),
        (FraudProcessWithSubprocess, List(SecondSourceName)), (MarketingProcess, List(DefaultSourceName)),
      )),
      (admin, fraudNotSharedSourceComponentId, List((FraudProcessWithNotSharedSource, List(DefaultSourceName)))),
      (admin, fraudCustomerDataEnricherComponentId, List((CanceledFraudProcessWith2Enrichers, List(DefaultCustomName, SecondCustomName)))),
      (admin, filterComponentId, List(
        (DeployedFraudProcessWith2Filters, List(DefaultFilterName, SecondFilterName)),
        (FraudProcessWithSubprocess, List(SecondFilterName)),
        (FraudSubprocess, List(SubprocessFilterName)),
      )),
    )

    forAll(testingData) { (user: LoggedUser, componentId: ComponentId, expected: List[(BaseProcessDetails[_], List[String])] ) =>
      val result = defaultComponentService.getComponentUsages(componentId)(user).futureValue
      val componentProcesses = expected.map{ case (process, nodesId) => ComponentUsagesInScenario(process, nodesId) }
      result shouldBe Right(componentProcesses)
    }
  }

  it should "return return error when component doesn't exist" in {
    val processService = createDbProcessService(categoryService)
    val defaultComponentService = DefaultComponentService(globalConfig, processingTypeDataProvider, processService, categoryService)
    val notExistComponentId = ComponentId("not-exist")
    val result = defaultComponentService.getComponentUsages(notExistComponentId)(admin).futureValue
    result shouldBe Left(ComponentNotFoundError(notExistComponentId))
  }

  private def createDbProcessService(processCategoryService: ProcessCategoryService, processes: List[ProcessWithJson] = Nil): DBProcessService =
    new DBProcessService(
      managerActor = TestFactory.newDummyManagerActor(),
      requestTimeLimit = Duration.ofMinutes(1),
      newProcessPreparer = TestFactory.createNewProcessPreparer(),
      processCategoryService = processCategoryService,
      processResolving = TestFactory.processResolving,
      repositoryManager = TestFactory.newDummyRepositoryManager(),
      fetchingProcessRepository = new MockFetchingProcessRepository(processes),
      processActionRepository = TestFactory.newDummyActionRepository(),
      processRepository = TestFactory.newDummyWriteProcessRepository()
    )

  private def cid(processingType: ProcessingType, name: String, componentType: ComponentType): ComponentId =
    ComponentId.default(processingType, name, componentType)

  private def bid(componentType: ComponentType): ComponentId =
    ComponentId.forBaseComponent(componentType)
}
