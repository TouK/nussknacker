package pl.touk.nussknacker.ui.component

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentType._
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}
import pl.touk.nussknacker.engine.definition.DefaultComponentIdProvider
import pl.touk.nussknacker.engine.processingtypesetup.ProcessingMode
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.{CategoriesConfig, ProcessingTypeData, ProcessingTypeSetup}
import pl.touk.nussknacker.restmodel.component.NodeUsageData.{FragmentUsageData, ScenarioUsageData}
import pl.touk.nussknacker.restmodel.component.{
  ComponentLink,
  ComponentListElement,
  ComponentUsagesInScenario,
  NodeUsageData
}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.scenariodetails.EngineSetupName
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures}
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes._
import pl.touk.nussknacker.ui.api.helpers.{
  MockDeploymentManager,
  MockFetchingProcessRepository,
  MockManagerProvider,
  TestFactory
}
import pl.touk.nussknacker.ui.component.ComponentModelData._
import pl.touk.nussknacker.ui.component.ComponentTestProcessData._
import pl.touk.nussknacker.ui.component.DefaultsComponentGroupName._
import pl.touk.nussknacker.ui.component.DefaultsComponentIcon._
import pl.touk.nussknacker.ui.component.DynamicComponentProvider._
import pl.touk.nussknacker.ui.config.ComponentLinkConfig._
import pl.touk.nussknacker.ui.config.{ComponentLinkConfig, ComponentLinksConfigExtractor}
import pl.touk.nussknacker.ui.definition.TestAdditionalUIConfigProvider
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.process.{
  ConfigProcessCategoryService,
  DBProcessService,
  ProcessCategoryService,
  UserCategoryService
}
import pl.touk.nussknacker.ui.security.api.LoggedUser

class DefaultComponentServiceSpec
    extends AnyFlatSpec
    with Matchers
    with PatientScalaFutures
    with EitherValuesDetailedMessage
    with OptionValues {

  import org.scalatest.prop.TableDrivenPropertyChecks._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val executionGroupName: ComponentGroupName  = ComponentGroupName("execution")
  private val responseGroupName: ComponentGroupName   = ComponentGroupName("response")
  private val hiddenGroupName: ComponentGroupName     = ComponentGroupName("hidden")
  private val overriddenGroupName: ComponentGroupName = ComponentGroupName("OverriddenGroupName")
  private val overriddenIcon                          = "OverriddenIcon.svg"
  private val filterDocsUrl = "https://nussknacker.io/documentation/docs/scenarios_authoring/BasicNodes#filter"

  private val usagesLinkId = "usages"
  private val invokeLinkId = "invoke"
  private val editLinkId   = "edit"
  private val filterLinkId = "filter"

  private val linkConfigs = List(
    ComponentLinkConfig.create(
      usagesLinkId,
      s"Usages of $ComponentNameTemplate",
      s"/assets/components/links/usages.svg",
      s"https://list-of-usages.com/$ComponentIdTemplate/",
      None
    ),
    ComponentLinkConfig.create(
      invokeLinkId,
      s"Invoke component $ComponentNameTemplate",
      s"/assets/components/links/invoke.svg",
      s"https://components.com/$ComponentIdTemplate/Invoke",
      Some(List(Enricher, Processor))
    ),
    ComponentLinkConfig.create(
      editLinkId,
      s"Edit component $ComponentNameTemplate",
      "/assets/components/links/edit.svg",
      s"https://components.com/$ComponentIdTemplate/",
      Some(List(CustomNode, Enricher, Processor))
    ),
    ComponentLinkConfig.create(
      filterLinkId,
      s"Custom link $ComponentNameTemplate",
      "https://other-domain.com/assets/components/links/filter.svg",
      s"https://components.com/$ComponentIdTemplate/filter",
      Some(List(Filter))
    ),
  )

  private val filterDocsLink = ComponentLink.createDocumentationLink(filterDocsUrl)

  // We disable kafka ComponentProvider from kafkaLite, which is unnecessarily added to classpath when running in Idea...
  private val disableKafkaLite = "kafka.disabled: true"

  private val componentLinksConfig = ComponentLinksConfigExtractor.extract(
    ConfigFactory.parseString(
      s"""
      componentLinks: [
        ${linkConfigs
          .map { link =>
            s"""{
           | id: "${link.id}",
           | title: "${link.title}",
           | url: "${link.url}",
           | icon: "${link.icon}",
           | ${link.supportedComponentTypes
                .map(types => s"""supportedComponentTypes: [${types.mkString(",")}]""")
                .getOrElse("")}
           | }""".stripMargin
          }
          .mkString(",\n")}
      ]
    """
    )
  )

  private val overrideKafkaSinkComponentId    = ComponentId(s"$Sink-$KafkaAvroProvidedComponentName")
  private val overrideKafkaSourceComponentId  = ComponentId(s"$Source-$KafkaAvroProvidedComponentName")
  private val customerDataEnricherComponentId = ComponentId(CustomerDataEnricherName)
  private val sharedEnricherComponentId       = ComponentId(SharedEnricherName)
  private val customStreamComponentId         = ComponentId(CustomStreamName)
  private val sharedSourceComponentId         = ComponentId(SharedSourceName)
  private val sharedProvidedComponentId       = ComponentId(SharedProvidedComponentName)

  private val streamingConfig: Config = ConfigFactory.parseString(s"""
       |{
       |  componentsUiConfig {
       |    $CustomerDataEnricherName {
       |      icon: "$overriddenIcon"
       |      componentGroup: "$responseGroupName"
       |      componentId: "$customerDataEnricherComponentId"
       |    },
       |    $Filter {
       |      icon: "$overriddenIcon"
       |      docsUrl: "$filterDocsUrl"
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
       |    $disableKafkaLite
       |  }
       |}
       |""".stripMargin)

  private val fraudConfig: Config = ConfigFactory.parseString(s"""
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
       |      docsUrl: "$filterDocsUrl"
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
       |    $disableKafkaLite
       |  }
       |}
       |""".stripMargin)

  private val wrongConfig: Config = ConfigFactory.parseString(s"""
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
       |    $disableKafkaLite
       |  }
       |}
       |""".stripMargin)

  private val categoryService = ConfigProcessCategoryService(
    ConfigFactory.empty,
    Map(
      Streaming -> CategoriesConfig(MarketingAllCategories),
      Fraud     -> CategoriesConfig(FraudAllCategories)
    )
  )

  private val baseComponents: List[ComponentListElement] =
    List(
      baseComponent(Filter, overriddenIcon, BaseGroupName, AllCategories),
      baseComponent(Split, SplitIcon, BaseGroupName, AllCategories),
      baseComponent(Switch, componentName = "choice", SwitchIcon, BaseGroupName, AllCategories),
      baseComponent(Variable, VariableIcon, BaseGroupName, AllCategories),
      baseComponent(MapVariable, MapVariableIcon, BaseGroupName, AllCategories),
    )

  private def prepareSharedComponents(implicit user: LoggedUser): List[ComponentListElement] =
    List(
      sharedComponent(
        SharedSourceName,
        SourceIcon,
        Source,
        SourcesGroupName,
        List(CategoryMarketing) ++ FraudAllCategories
      ),
      sharedComponent(
        SharedSinkName,
        SinkIcon,
        Sink,
        executionGroupName,
        List(CategoryMarketing) ++ FraudWithoutSupperCategories
      ),
      sharedComponent(
        SharedEnricherName,
        overriddenIcon,
        Enricher,
        EnrichersGroupName,
        List(CategoryMarketing) ++ FraudWithoutSupperCategories
      ),
      sharedComponent(
        SharedProvidedComponentName,
        ProcessorIcon,
        Processor,
        executionGroupName,
        List(CategoryMarketingTests, CategoryFraudTests)
      ),
      sharedComponent(
        KafkaAvroProvidedComponentName,
        SourceIcon,
        Source,
        SourcesGroupName,
        List(CategoryMarketingTests, CategoryFraudTests),
        componentId = Some(overrideKafkaSourceComponentId)
      ),
      sharedComponent(
        KafkaAvroProvidedComponentName,
        SinkIcon,
        Sink,
        executionGroupName,
        List(CategoryMarketingTests, CategoryFraudTests),
        componentId = Some(overrideKafkaSinkComponentId)
      ),
    )

  private def prepareMarketingComponents(implicit user: LoggedUser): List[ComponentListElement] = List(
    marketingComponent(
      CustomStreamName,
      CustomNodeIcon,
      CustomNode,
      CustomGroupName,
      MarketingWithoutSuperCategories,
      componentId = Some(customStreamComponentId)
    ),
    marketingComponent(
      CustomerDataEnricherName,
      overriddenIcon,
      Enricher,
      responseGroupName,
      List(CategoryMarketing),
      componentId = Some(customerDataEnricherComponentId)
    ),
    marketingComponent(
      FuseBlockServiceName,
      ProcessorIcon,
      Processor,
      executionGroupName,
      MarketingWithoutSuperCategories
    ),
    marketingComponent(MonitorName, SinkIcon, Sink, executionGroupName, MarketingAllCategories),
    marketingComponent(
      OptionalCustomStreamName,
      CustomNodeIcon,
      CustomNode,
      OptionalEndingCustomGroupName,
      MarketingWithoutSuperCategories
    ),
    marketingComponent(SuperMarketingSourceName, SourceIcon, Source, SourcesGroupName, List(CategoryMarketingSuper)),
    marketingComponent(
      SingleProvidedComponentName,
      ProcessorIcon,
      Processor,
      executionGroupName,
      List(CategoryMarketingTests)
    ),
  )

  private def prepareFraudComponents(implicit user: LoggedUser): List[ComponentListElement] = List(
    fraudComponent(CustomStreamName, CustomNodeIcon, CustomNode, CustomGroupName, FraudWithoutSupperCategories),
    fraudComponent(CustomerDataEnricherName, EnricherIcon, Enricher, EnrichersGroupName, List(CategoryFraud)),
    fraudComponent(FuseBlockServiceName, ProcessorIcon, Processor, executionGroupName, FraudWithoutSupperCategories),
    fraudComponent(
      OptionalCustomStreamName,
      CustomNodeIcon,
      CustomNode,
      OptionalEndingCustomGroupName,
      FraudWithoutSupperCategories
    ),
    fraudComponent(SecondMonitorName, SinkIcon, Sink, executionGroupName, FraudAllCategories),
    fraudComponent(SingleProvidedComponentName, ProcessorIcon, Processor, executionGroupName, List(CategoryFraudTests)),
    fraudComponent(NotSharedSourceName, SourceIcon, Source, SourcesGroupName, FraudAllCategories),
    fraudComponent(FraudSinkName, SinkIcon, Sink, executionGroupName, FraudAllCategories),
  )

  private def sharedComponent(
      name: String,
      icon: String,
      componentType: ComponentType,
      componentGroupName: ComponentGroupName,
      categories: List[String],
      componentId: Option[ComponentId] = None
  )(implicit user: LoggedUser) = {
    val id         = componentId.getOrElse(ComponentId(name))
    val links      = createLinks(id, name, componentType)
    val usageCount = componentCount(id, user)

    val availableCategories =
      if (categories.isEmpty)
        new UserCategoryService(categoryService).getUserCategories(user).sorted
      else
        categories.filter(user.can(_, Permission.Read)).sorted

    ComponentListElement(id, name, icon, componentType, componentGroupName, availableCategories, links, usageCount)
  }

  private val fragmentMarketingComponents: List[ComponentListElement] = MarketingAllCategories.map(cat => {
    val componentId = cid(Streaming, cat, Fragments)
    val icon        = DefaultsComponentIcon.fromComponentType(Fragments)
    val links       = createLinks(componentId, cat, Fragments)
    ComponentListElement(componentId, cat, icon, Fragments, FragmentsGroupName, List(cat), links, 0)
  })

  private val fragmentFraudComponents: List[ComponentListElement] = FraudAllCategories.map(cat => {
    val componentId = cid(Fraud, cat, Fragments)
    val icon        = if (cat == CategoryFraud) overriddenIcon else DefaultsComponentIcon.fromComponentType(Fragments)
    val links       = createLinks(componentId, cat, Fragments)
    ComponentListElement(componentId, cat, icon, Fragments, FragmentsGroupName, List(cat), links, 0)
  })

  private def prepareComponents(implicit user: LoggedUser): List[ComponentListElement] =
    baseComponents ++ prepareSharedComponents ++ prepareMarketingComponents ++ prepareFraudComponents ++ fragmentMarketingComponents ++ fragmentFraudComponents

  private val fragmentFromCategories = AllCategories
    .flatMap(cat =>
      categoryService
        .getTypeForCategory(cat)
        .map(processingType => createFragment(cat, category = cat, processingType = processingType))
    )
    .toSet

  private def marketingComponent(
      name: String,
      icon: String,
      componentType: ComponentType,
      componentGroupName: ComponentGroupName,
      categories: List[String],
      componentId: Option[ComponentId] = None
  )(implicit user: LoggedUser) =
    createComponent(Streaming, name, icon, componentType, componentGroupName, categories, componentId)

  private def fraudComponent(
      name: String,
      icon: String,
      componentType: ComponentType,
      componentGroupName: ComponentGroupName,
      categories: List[String],
      componentId: Option[ComponentId] = None
  )(implicit user: LoggedUser) =
    createComponent(Fraud, name, icon, componentType, componentGroupName, categories, componentId)

  private def createComponent(
      processingType: String,
      name: String,
      icon: String,
      componentType: ComponentType,
      componentGroupName: ComponentGroupName,
      categories: List[String],
      componentId: Option[ComponentId] = None
  )(implicit user: LoggedUser) = {
    val compId     = componentId.getOrElse(cid(processingType, name, componentType))
    val links      = createLinks(compId, name, componentType)
    val usageCount = componentCount(compId, user)
    ComponentListElement(compId, name, icon, componentType, componentGroupName, categories, links, usageCount)
  }

  private def baseComponent(
      componentType: ComponentType,
      icon: String,
      componentGroupName: ComponentGroupName,
      categories: List[String]
  ): ComponentListElement =
    baseComponent(componentType, componentType.toString, icon, componentGroupName, categories)

  private def baseComponent(
      componentType: ComponentType,
      componentName: String,
      icon: String,
      componentGroupName: ComponentGroupName,
      categories: List[String]
  ): ComponentListElement = {
    val componentId = bid(componentType)
    val docsLinks   = if (componentType == Filter) List(filterDocsLink) else Nil
    val links       = docsLinks ++ createLinks(componentId, componentName, componentType)
    ComponentListElement(componentId, componentName, icon, componentType, componentGroupName, categories, links, 0)
  }

  private def createLinks(
      componentId: ComponentId,
      componentName: String,
      componentType: ComponentType
  ): List[ComponentLink] =
    linkConfigs
      .filter(_.isAvailable(componentType))
      .map(_.toComponentLink(componentId, componentName))

  private def componentCount(componentId: ComponentId, user: LoggedUser) = {
    val sourceComponentId = ComponentId(SharedSourceName)
    val sinkComponentId   = ComponentId(SharedSinkName)

    componentId match {
      // Order is matter, first should be condition with more number of categories
      case _ @id if id == sourceComponentId && hasAccess(user, CategoryFraud, CategoryFraudTests, CategoryMarketing) =>
        3
      case _ @id if id == sinkComponentId && hasAccess(user, CategoryFraud, CategoryFraudTests, CategoryMarketing) => 3

      case _ @id if id == sourceComponentId && hasAccess(user, CategoryFraud, CategoryFraudTests) => 2
      case _ @id if id == sinkComponentId && hasAccess(user, CategoryFraud, CategoryFraudTests)   => 2

      case _ @id if id == sourceComponentId && hasAccess(user, CategoryFraudTests) => 1
      case _ @id if id == sinkComponentId && hasAccess(user, CategoryFraudTests)   => 1

      case _ @id if id == sourceComponentId && hasAccess(user, CategoryFraud) => 1
      case _ @id if id == sinkComponentId && hasAccess(user, CategoryFraud)   => 1

      case _ @id if id == sourceComponentId && hasAccess(user, CategoryMarketing) => 1
      case _ @id if id == sinkComponentId && hasAccess(user, CategoryMarketing)   => 1

      case _ => 0
    }
  }

  private def hasAccess(user: LoggedUser, categories: Category*): Boolean =
    categories.forall(cat => user.can(cat, Permission.Read))

  private val admin = TestFactory.adminUser()

  private val marketingFullUser = TestFactory.userWithCategoriesReadPermission(
    username = "marketingFullUser",
    categories = MarketingWithoutSuperCategories
  )

  private val marketingTestsUser = TestFactory.userWithCategoriesReadPermission(
    username = "marketingTestsUser",
    categories = List(CategoryMarketingTests)
  )

  private val fraudFullUser =
    TestFactory.userWithCategoriesReadPermission(username = "fraudFullUser", categories = FraudWithoutSupperCategories)
  private val fraudTestsUser =
    TestFactory.userWithCategoriesReadPermission(username = "fraudTestsUser", categories = List(CategoryFraudTests))

  private val processingTypeDataMap: Map[Category, ProcessingTypeData] = Map(
    Streaming -> (LocalModelData(streamingConfig, ComponentMarketingTestConfigCreator), MarketingAllCategories),
    Fraud     -> (LocalModelData(fraudConfig, ComponentFraudTestConfigCreator), FraudAllCategories)
  ).transform { case (_, (modelData, categories)) =>
    ProcessingTypeData.createProcessingTypeData(
      MockManagerProvider,
      new MockDeploymentManager,
      modelData,
      ConfigFactory.empty(),
      CategoriesConfig(categories)
    )
  }

  private val processingTypeDataProvider = new MapBasedProcessingTypeDataProvider(
    processingTypeDataMap,
    (ComponentIdProviderFactory.createUnsafe(processingTypeDataMap, categoryService), categoryService)
  )

  it should "return components for each user" in {
    val processes      = List(MarketingProcess, FraudProcess, FraudTestProcess, ArchivedFraudProcess)
    val processService = createDbProcessService(categoryService, processes ++ fragmentFromCategories.toList)
    val defaultComponentService =
      DefaultComponentService(
        componentLinksConfig,
        processingTypeDataProvider,
        processService,
        TestAdditionalUIConfigProvider
      )

    def filterUserComponents(user: LoggedUser, categories: List[String]): List[ComponentListElement] =
      prepareComponents(user)
        .map(c => c -> categories.intersect(c.categories))
        .filter(seq => seq._2.nonEmpty)
        .map(seq => seq._1.copy(categories = seq._2))

    val adminComponents          = prepareComponents(admin)
    val marketingFullComponents  = filterUserComponents(marketingFullUser, MarketingWithoutSuperCategories)
    val marketingTestsComponents = filterUserComponents(marketingTestsUser, List(CategoryMarketingTests))
    val fraudFullComponents      = filterUserComponents(fraudFullUser, FraudWithoutSupperCategories)
    val fraudTestsComponents     = filterUserComponents(fraudTestsUser, List(CategoryFraudTests))

    val testingData = Table(
      ("user", "expectedComponents", "possibleCategories"),
      (admin, adminComponents, AllCategories),
      (marketingFullUser, marketingFullComponents, MarketingWithoutSuperCategories),
      (marketingTestsUser, marketingTestsComponents, List(CategoryMarketingTests)),
      (fraudFullUser, fraudFullComponents, FraudWithoutSupperCategories),
      (fraudTestsUser, fraudTestsComponents, List(CategoryFraudTests)),
    )

    forAll(testingData) {
      (user: LoggedUser, expectedComponents: List[ComponentListElement], possibleCategories: List[String]) =>
        val components = defaultComponentService.getComponentsList(user).futureValue

        def counts(list: List[ComponentListElement]) = list.map(el => el.id -> el.usageCount).toMap

        val returnedCounts = counts(components)
        val expectedCounts = counts(expectedComponents)
        // we don't do exact matching, to avoid handling autoLoaded components here
        returnedCounts.keySet should contain allElementsOf expectedCounts.keySet
        returnedCounts should contain allElementsOf expectedCounts
        components should contain allElementsOf expectedComponents

        // Components should contain only user categories
        val componentsCategories = components.flatMap(_.categories).distinct.sorted
        componentsCategories.diff(possibleCategories).isEmpty shouldBe true

        components.foreach(comp => {
          // See linksConfig
          val availableLinksId = comp.componentType match {
            case Processor | Enricher => List(usagesLinkId, invokeLinkId, editLinkId)
            case CustomNode           => List(usagesLinkId, editLinkId)
            case Filter               => List(usagesLinkId, filterLinkId)
            case _                    => List(usagesLinkId)
          }

          val availableDocsLinksId = comp.componentType match {
            case Filter => List(filterDocsLink.id)
            case _      => Nil
          }

          // Base components from providers contain more links because of documentation
          comp.links.map(_.id) should contain allElementsOf availableDocsLinksId ++ availableLinksId

          comp.links
            .filter(l => availableLinksId.contains(l.id))
            .foreach(link => {
              link.title should include(comp.name)
              link.url.toString should include(comp.id.value)
            })
        })
    }
  }

  it should "throws exception when components are wrong configured" in {
    import WrongConfigurationAttribute._
    val badProcessingTypeDataMap = Map(
      Streaming -> (LocalModelData(streamingConfig, ComponentMarketingTestConfigCreator), MarketingAllCategories),
      Fraud     -> (LocalModelData(wrongConfig, WronglyConfiguredConfigCreator), FraudAllCategories),
    ).map { case (processingType, (modelData, categories)) =>
      processingType -> ProcessingTypeData.createProcessingTypeData(
        MockManagerProvider,
        new MockDeploymentManager,
        modelData,
        ConfigFactory.empty(),
        CategoriesConfig(categories)
      )
    }
    val componentObjectsService = new ComponentObjectsService(categoryService)
    val componentObjectsMap =
      badProcessingTypeDataMap.transform(componentObjectsService.prepareWithoutFragmentsAndAdditionalUIConfigs)
    val componentIdProvider = new DefaultComponentIdProvider(componentObjectsMap.transform {
      case (_, componentsObjects) => componentsObjects.config
    })

    val expectedWrongConfigurations = List(
      ComponentWrongConfiguration(sharedSourceComponentId, NameAttribute, List(SharedSourceName, SharedSourceV2Name)),
      ComponentWrongConfiguration(
        sharedSourceComponentId,
        ComponentGroupNameAttribute,
        List(SourcesGroupName, executionGroupName)
      ),
      // ComponentWrongConfiguration(ComponentId.create(hiddenMarketingCustomerDataEnricherName), ComponentGroupNameAttribute, List(HiddenGroupName, CustomGroupName)), //TODO: right now we don't support hidden components, see how works UIProcessObjectsFactory.prepareUIProcessObjects
      ComponentWrongConfiguration(
        sharedSourceComponentId,
        IconAttribute,
        List(DefaultsComponentIcon.fromComponentType(Source), overriddenIcon)
      ),
      ComponentWrongConfiguration(sharedEnricherComponentId, ComponentTypeAttribute, List(Enricher, Processor)),
      ComponentWrongConfiguration(
        sharedEnricherComponentId,
        IconAttribute,
        List(overriddenIcon, DefaultsComponentIcon.fromComponentType(Processor))
      ),
      ComponentWrongConfiguration(
        sharedEnricherComponentId,
        ComponentGroupNameAttribute,
        List(EnrichersGroupName, ServicesGroupName)
      ),
      ComponentWrongConfiguration(
        bid(Filter),
        IconAttribute,
        List(overriddenIcon, DefaultsComponentIcon.fromComponentType(Filter))
      ),
      ComponentWrongConfiguration(
        sharedProvidedComponentId,
        IconAttribute,
        List(DefaultsComponentIcon.fromComponentType(Processor), overriddenIcon)
      ),
      ComponentWrongConfiguration(
        sharedProvidedComponentId,
        ComponentGroupNameAttribute,
        List(executionGroupName, overriddenGroupName)
      ),
    )

    val wrongConfigurations = intercept[ComponentConfigurationException] {
      ComponentsValidator.checkUnsafe(componentObjectsMap, componentIdProvider)
    }.wrongConfigurations

    wrongConfigurations.toList should contain allElementsOf expectedWrongConfigurations
  }

  it should "return components usage" in {
    val processes = List(
      MarketingProcess,
      FraudProcess,
      FraudProcessWithNotSharedSource,
      CanceledFraudProcessWith2Enrichers,
      DeployedFraudProcessWith2Filters,
      ArchivedFraudProcess,
      FraudProcessWithFragment,
      FraudFragment
    )

    val fraudNotSharedSourceComponentId      = cid(Fraud, NotSharedSourceName, Source)
    val fraudCustomerDataEnricherComponentId = cid(Fraud, CustomerDataEnricherName, Enricher)
    val sharedSourceComponentId              = ComponentId(SharedSourceName) // it's shared id - merged at configs file
    val fragmentComponentId                  = cid(Fraud, FraudFragmentName, Fragments)
    val filterComponentId                    = bid(Filter)

    val processService = createDbProcessService(categoryService, processes)
    val defaultComponentService =
      DefaultComponentService(
        componentLinksConfig,
        processingTypeDataProvider,
        processService,
        TestAdditionalUIConfigProvider
      )

    val testingData = Table(
      ("user", "componentId", "expected"),
      (admin, fragmentComponentId, List((FraudProcessWithFragment, List(ScenarioUsageData(FraudFragmentName))))),
      (
        admin,
        sharedSourceComponentId,
        List(
          (CanceledFraudProcessWith2Enrichers, List(ScenarioUsageData(DefaultSourceName))),
          (DeployedFraudProcessWith2Filters, List(ScenarioUsageData(DefaultSourceName))),
          (FraudProcess, List(ScenarioUsageData(DefaultSourceName))),
          (FraudProcessWithFragment, List(ScenarioUsageData(SecondSourceName))),
          (MarketingProcess, List(ScenarioUsageData(DefaultSourceName))),
        )
      ),
      (
        admin,
        fraudNotSharedSourceComponentId,
        List((FraudProcessWithNotSharedSource, List(ScenarioUsageData(DefaultSourceName))))
      ),
      (
        admin,
        fraudCustomerDataEnricherComponentId,
        List(
          (
            CanceledFraudProcessWith2Enrichers,
            List(ScenarioUsageData(DefaultCustomName), ScenarioUsageData(SecondCustomName))
          )
        )
      ),
      (
        admin,
        filterComponentId,
        List(
          (
            DeployedFraudProcessWith2Filters,
            List(ScenarioUsageData(DefaultFilterName), ScenarioUsageData(SecondFilterName))
          ),
          (FraudFragment, List(ScenarioUsageData(FragmentFilterName))),
          (
            FraudProcessWithFragment,
            List(ScenarioUsageData(SecondFilterName), FragmentUsageData(FraudFragment.name.value, FragmentFilterName))
          ),
        )
      ),
    )

    forAll(testingData) {
      (
          user: LoggedUser,
          componentId: ComponentId,
          expected: List[(ScenarioWithDetailsEntity[_], List[NodeUsageData])]
      ) =>
        val result = defaultComponentService
          .getComponentUsages(componentId)(user)
          .futureValue
          .map(_.map(n => n.copy(nodesUsagesData = n.nodesUsagesData.sorted)))
        val componentProcesses = expected.map { case (process, nodesUsagesData) =>
          DefaultComponentService.toComponentUsagesInScenario(process, nodesUsagesData.sorted)
        }
        result shouldBe Right(componentProcesses)
    }
  }

  it should "return return error when component doesn't exist" in {
    val processService = createDbProcessService(categoryService)
    val defaultComponentService =
      DefaultComponentService(
        componentLinksConfig,
        processingTypeDataProvider,
        processService,
        TestAdditionalUIConfigProvider
      )
    val notExistComponentId = ComponentId("not-exist")
    val result              = defaultComponentService.getComponentUsages(notExistComponentId)(admin).futureValue
    result shouldBe Left(ComponentNotFoundError(notExistComponentId))
  }

  private def createDbProcessService(
      processCategoryService: ProcessCategoryService,
      processes: List[ScenarioWithDetailsEntity[DisplayableProcess]] = Nil
  ): DBProcessService =
    new DBProcessService(
      deploymentService = TestFactory.deploymentService(),
      newProcessPreparer = TestFactory.createNewProcessPreparer(),
      getProcessingTypeSetupProvider = () =>
        _ => ProcessingTypeSetup(ProcessingMode.Streaming, EngineSetupName("Test")),
      getProcessCategoryService = () => processCategoryService,
      processResolving = TestFactory.processResolving,
      dbioRunner = TestFactory.newDummyDBIOActionRunner(),
      fetchingProcessRepository = MockFetchingProcessRepository.withProcessesDetails(processes),
      processActionRepository = TestFactory.newDummyActionRepository(),
      processRepository = TestFactory.newDummyWriteProcessRepository(),
      processValidation = TestFactory.processValidation
    )

  private def cid(processingType: ProcessingType, name: String, componentType: ComponentType): ComponentId =
    ComponentId.default(processingType, name, componentType)

  private def bid(componentType: ComponentType): ComponentId =
    ComponentId.forBaseComponent(componentType)

  private implicit def ordering: Ordering[NodeUsageData] = (x: NodeUsageData, y: NodeUsageData) => {
    x.nodeId.compareTo(y.nodeId)
  }

}
