package pl.touk.nussknacker.ui.definition.component

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Inside.inside
import org.scalatest.OptionValues
import org.scalatest.exceptions.TestFailedException
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.api.component.Component.AllowedProcessingModes
import pl.touk.nussknacker.engine.api.component.ComponentType._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessingType, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultsComponentGroupName._
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultsComponentIcon
import pl.touk.nussknacker.engine.definition.component.defaultconfig.DefaultsComponentIcon._
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.component.{ComponentLink, ComponentListElement, NodeUsageData}
import pl.touk.nussknacker.restmodel.component.NodeUsageData.{FragmentUsageData, ScenarioUsageData}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, PatientScalaFutures, ValidatedValuesDetailedMessage}
import pl.touk.nussknacker.test.mock.{MockFetchingProcessRepository, MockManagerProvider}
import pl.touk.nussknacker.test.utils.domain.TestFactory
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil.createFragmentEntity
import pl.touk.nussknacker.ui.api.ScenarioStatusPresenter
import pl.touk.nussknacker.ui.config.{ComponentLinkConfig, ComponentLinksConfigExtractor}
import pl.touk.nussknacker.ui.config.ComponentLinkConfig._
import pl.touk.nussknacker.ui.definition.AlignedComponentsDefinitionProvider
import pl.touk.nussknacker.ui.definition.component.ComponentListQueryOptions.{
  FetchAllWithoutUsages,
  FetchAllWithUsages,
  FetchNonFragmentsWithoutUsages,
  FetchNonFragmentsWithUsages
}
import pl.touk.nussknacker.ui.definition.component.ComponentModelData._
import pl.touk.nussknacker.ui.definition.component.ComponentTestProcessData._
import pl.touk.nussknacker.ui.definition.component.DynamicComponentProvider._
import pl.touk.nussknacker.ui.process.DBProcessService
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.fragment.DefaultFragmentRepository
import pl.touk.nussknacker.ui.process.processingtype.{ProcessingTypeData, ScenarioParametersService}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeData.SchedulingForProcessingType
import pl.touk.nussknacker.ui.process.processingtype.loader.ProcessingTypeDataLoader
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, ImpersonatedUser, LoggedUser, RealLoggedUser}
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission

import java.net.URI
import scala.annotation.tailrec

class DefaultComponentServiceSpec
    extends AnyFlatSpec
    with Matchers
    with PatientScalaFutures
    with EitherValuesDetailedMessage
    with ValidatedValuesDetailedMessage
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

  private val invokePermission: GlobalPermission = "InvokePermissionExample"

  private val linkConfigs = List(
    createLinkConfig(
      usagesLinkId,
      s"Usages of $ComponentNameTemplate",
      s"/assets/components/links/usages.svg",
      s"https://list-of-usages.com/$ComponentIdTemplate/",
      None,
      None
    ),
    createLinkConfig(
      invokeLinkId,
      s"Invoke component $ComponentNameTemplate",
      s"/assets/components/links/invoke.svg",
      s"https://components.com/$ComponentIdTemplate/Invoke",
      Some(List(Service)),
      Some(invokePermission),
    ),
    createLinkConfig(
      editLinkId,
      s"Edit component $ComponentNameTemplate",
      "/assets/components/links/edit.svg",
      s"https://components.com/$ComponentIdTemplate/",
      Some(List(CustomComponent, Service)),
      None,
    ),
    createLinkConfig(
      filterLinkId,
      s"Custom link $ComponentNameTemplate",
      "https://other-domain.com/assets/components/links/filter.svg",
      s"https://components.com/$ComponentIdTemplate/filter",
      Some(List(BuiltIn)),
      None,
    ),
  )

  private val filterDocsLink = ComponentLink.createDocumentationLink(filterDocsUrl)

  private val componentLinksConfig = ComponentLinksConfigExtractor.extract(
    ConfigFactory.parseString(
      s"""
      componentLinks: [
        ${linkConfigs
          .map { link =>
            s"""{
           | id: "${link.id}"
           | title: "${link.title}"
           | url: "${link.url}"
           | icon: "${link.icon}"
           | ${link.supportedComponentTypes
                .map(types => s"""supportedComponentTypes: [${types.mkString(",")}]""")
                .getOrElse("")}
           | ${link.requiredPermission.map(permission => s"""requiredPermission: "$permission"""").getOrElse("")}
           | }""".stripMargin
          }
          .mkString(",\n")}
      ]
    """
    )
  )

  private val overrideSinkComponentId         = DesignerWideComponentId(s"$Sink-$SourceSinkSameNameComponentName")
  private val overrideSourceComponentId       = DesignerWideComponentId(s"$Source-$SourceSinkSameNameComponentName")
  private val customerDataEnricherComponentId = DesignerWideComponentId(CustomerDataEnricherName)
  private val sharedEnricherComponentId       = DesignerWideComponentId(SharedEnricherName)
  private val customStreamComponentId         = DesignerWideComponentId(CustomStreamName)
  private val sharedSourceComponentId         = DesignerWideComponentId(SharedSourceName)
  private val sharedProvidedComponentId       = DesignerWideComponentId(SharedProvidedComponentName)

  private val streamingConfig: Config = ConfigFactory.parseString(s"""
       |{
       |  componentsUiConfig {
       |    $CustomerDataEnricherName {
       |      icon: "$overriddenIcon"
       |      componentGroup: "$responseGroupName"
       |      componentId: "$customerDataEnricherComponentId"
       |    },
       |    ${BuiltInComponentId.Filter.name} {
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
       |    ${ComponentId(Source, SourceSinkSameNameComponentName)} {
       |      componentId: "$overrideSourceComponentId"
       |    }
       |    ${ComponentId(Sink, SourceSinkSameNameComponentName)} {
       |      componentId: "$overrideSinkComponentId"
       |    }
       |  }
       |
       |  componentsGroupMapping {
       |    "$SinksGroupName": "$executionGroupName",
       |    "$ServicesGroupName": "$executionGroupName",
       |    "$hiddenGroupName": null
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
       |    ${BuiltInComponentId.Filter.name} {
       |      icon: "$overriddenIcon"
       |      docsUrl: "$filterDocsUrl"
       |    },
       |    $SharedEnricherName {
       |      icon: "$overriddenIcon"
       |    },
       |    $SharedProvidedComponentName {
       |      componentId: $SharedProvidedComponentName
       |    },
       |    ${ComponentId(Source, SourceSinkSameNameComponentName)} {
       |      componentId: "$overrideSourceComponentId"
       |    }
       |    ${ComponentId(Sink, SourceSinkSameNameComponentName)} {
       |      componentId: "$overrideSinkComponentId"
       |    }
       |  }
       |
       |  componentsGroupMapping {
       |    "$SinksGroupName": "$executionGroupName",
       |    "$ServicesGroupName": "$executionGroupName",
       |    "$hiddenGroupName": null
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
       |}
       |""".stripMargin)

  private def baseComponents(implicit user: LoggedUser): List[ComponentListElement] =
    List(
      baseComponent(BuiltInComponentId.Filter, overriddenIcon, BaseGroupName, AllCategories),
      baseComponent(BuiltInComponentId.Split, SplitIcon, BaseGroupName, AllCategories),
      baseComponent(BuiltInComponentId.Choice, ChoiceIcon, BaseGroupName, AllCategories),
      baseComponent(BuiltInComponentId.Variable, VariableIcon, BaseGroupName, AllCategories),
      baseComponent(BuiltInComponentId.RecordVariable, RecordVariableIcon, BaseGroupName, AllCategories),
    )

  private def prepareSharedComponents(implicit user: LoggedUser): List[ComponentListElement] =
    List(
      sharedComponent(
        ComponentId(Source, SharedSourceName),
        SourceIcon,
        SourcesGroupName,
        nonDefaultAllowedProcessingModes = Some(AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream))
      ),
      sharedComponent(
        ComponentId(Sink, SharedSinkName),
        SinkIcon,
        executionGroupName,
      ),
      sharedComponent(
        ComponentId(Service, SharedEnricherName),
        overriddenIcon,
        EnrichersGroupName,
      ),
      sharedComponent(
        ComponentId(Service, SharedProvidedComponentName),
        ServiceIcon,
        executionGroupName,
      ),
      sharedComponent(
        ComponentId(Source, SourceSinkSameNameComponentName),
        SourceIcon,
        SourcesGroupName,
        designerWideComponentId = Some(overrideSourceComponentId),
        nonDefaultAllowedProcessingModes = Some(AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream))
      ),
      sharedComponent(
        ComponentId(Sink, SourceSinkSameNameComponentName),
        SinkIcon,
        executionGroupName,
        designerWideComponentId = Some(overrideSinkComponentId)
      ),
    )

  private def prepareMarketingComponents(implicit user: LoggedUser): List[ComponentListElement] = List(
    marketingComponent(
      ComponentId(CustomComponent, CustomStreamName),
      CustomComponentIcon,
      CustomGroupName,
      designerWideComponentId = Some(customStreamComponentId)
    ),
    marketingComponent(
      ComponentId(Service, CustomerDataEnricherName),
      overriddenIcon,
      responseGroupName,
      designerWideComponentId = Some(customerDataEnricherComponentId)
    ),
    marketingComponent(
      ComponentId(Service, FuseBlockServiceName),
      ServiceIcon,
      executionGroupName
    ),
    marketingComponent(ComponentId(Sink, MonitorName), SinkIcon, executionGroupName),
    marketingComponent(
      ComponentId(CustomComponent, OptionalCustomStreamName),
      CustomComponentIcon,
      OptionalEndingCustomGroupName
    ),
    marketingComponent(
      ComponentId(Source, SuperMarketingSourceName),
      SourceIcon,
      SourcesGroupName,
      nonDefaultAllowedProcessingModes = Some(AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream))
    ),
    marketingComponent(
      ComponentId(Source, NotSharedSourceName),
      SourceIcon,
      SourcesGroupName,
      nonDefaultAllowedProcessingModes = Some(AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream))
    ),
    marketingComponent(
      ComponentId(Service, SingleProvidedComponentName),
      ServiceIcon,
      executionGroupName
    ),
  )

  private def prepareFraudComponents(implicit user: LoggedUser): List[ComponentListElement] = List(
    fraudComponent(ComponentId(CustomComponent, CustomStreamName), CustomComponentIcon, CustomGroupName),
    fraudComponent(ComponentId(Service, CustomerDataEnricherName), EnricherIcon, EnrichersGroupName),
    fraudComponent(ComponentId(Service, FuseBlockServiceName), ServiceIcon, executionGroupName),
    fraudComponent(
      ComponentId(CustomComponent, OptionalCustomStreamName),
      CustomComponentIcon,
      OptionalEndingCustomGroupName
    ),
    fraudComponent(ComponentId(Sink, SecondMonitorName), SinkIcon, executionGroupName),
    fraudComponent(ComponentId(Service, SingleProvidedComponentName), ServiceIcon, executionGroupName),
    fraudComponent(
      ComponentId(Source, NotSharedSourceName),
      SourceIcon,
      SourcesGroupName,
      nonDefaultAllowedProcessingModes = Some(AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream))
    ),
    fraudComponent(ComponentId(Sink, FraudSinkName), SinkIcon, executionGroupName),
  )

  private def sharedComponent(
      componentId: ComponentId,
      icon: String,
      componentGroupName: ComponentGroupName,
      designerWideComponentId: Option[DesignerWideComponentId] = None,
      nonDefaultAllowedProcessingModes: Option[AllowedProcessingModes] = None
  )(implicit user: LoggedUser) = {
    val id         = designerWideComponentId.getOrElse(DesignerWideComponentId(componentId.name))
    val links      = createLinks(id, componentId)
    val usageCount = componentCount(id, user)

    val availableCategories = AllCategories.filter(user.can(_, Permission.Read)).sorted

    ComponentListElement(
      id,
      componentId.name,
      icon,
      componentId.`type`,
      componentGroupName,
      availableCategories,
      links,
      usageCount,
      nonDefaultAllowedProcessingModes.getOrElse(AllowedProcessingModes.All),
      componentId.name
    )
  }

  private def fragmentMarketingComponents(implicit loggedUser: LoggedUser): List[ComponentListElement] = {
    val cat                     = CategoryMarketing
    val componentId             = ComponentId(Fragment, cat)
    val designerWideComponentId = cid(ProcessingTypeStreaming, componentId)
    val icon                    = DefaultsComponentIcon.fromComponentId(componentId, None)
    val links                   = createLinks(designerWideComponentId, componentId)
    List(
      ComponentListElement(
        designerWideComponentId,
        cat,
        icon,
        Fragment,
        FragmentsGroupName,
        List(cat),
        links,
        0,
        AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream),
        componentId.name
      )
    )
  }

  private def fragmentFraudComponents(implicit loggedUser: LoggedUser): List[ComponentListElement] = {
    val cat                     = CategoryFraud
    val componentId             = ComponentId(Fragment, cat)
    val designerWideComponentId = cid(ProcessingTypeFraud, componentId)
    val links                   = createLinks(designerWideComponentId, componentId)
    List(
      ComponentListElement(
        designerWideComponentId,
        cat,
        DefaultsComponentIcon.FragmentIcon,
        Fragment,
        FragmentsGroupName,
        List(cat),
        links,
        0,
        AllowedProcessingModes.SetOf(ProcessingMode.UnboundedStream),
        componentId.name
      )
    )
  }

  // It would be good to rewrite these tests to make expected component list static which would also make expected values more readable
  private def prepareComponents(implicit user: LoggedUser): List[ComponentListElement] =
    baseComponents ++ prepareSharedComponents ++ prepareMarketingComponents ++ prepareFraudComponents ++ fragmentMarketingComponents ++ fragmentFraudComponents

  private def marketingComponent(
      componentId: ComponentId,
      icon: String,
      componentGroupName: ComponentGroupName,
      designerWideComponentId: Option[DesignerWideComponentId] = None,
      nonDefaultAllowedProcessingModes: Option[AllowedProcessingModes] = None
  )(implicit user: LoggedUser) =
    createComponent(
      ProcessingTypeStreaming,
      componentId,
      icon,
      componentGroupName,
      List(CategoryMarketing),
      designerWideComponentId,
      nonDefaultAllowedProcessingModes
    )

  private def fraudComponent(
      componentId: ComponentId,
      icon: String,
      componentGroupName: ComponentGroupName,
      designerWideComponentId: Option[DesignerWideComponentId] = None,
      nonDefaultAllowedProcessingModes: Option[AllowedProcessingModes] = None
  )(implicit user: LoggedUser) =
    createComponent(
      ProcessingTypeFraud,
      componentId,
      icon,
      componentGroupName,
      List(CategoryFraud),
      designerWideComponentId,
      nonDefaultAllowedProcessingModes
    )

  private def createComponent(
      processingType: String,
      componentId: ComponentId,
      icon: String,
      componentGroupName: ComponentGroupName,
      categories: List[String],
      designerWideComponentId: Option[DesignerWideComponentId] = None,
      nonDefaultAllowedProcessingModes: Option[AllowedProcessingModes] = None
  )(implicit user: LoggedUser) = {
    val compId     = designerWideComponentId.getOrElse(cid(processingType, componentId))
    val links      = createLinks(compId, componentId)
    val usageCount = componentCount(compId, user)
    ComponentListElement(
      compId,
      componentId.name,
      icon,
      componentId.`type`,
      componentGroupName,
      categories,
      links,
      usageCount,
      nonDefaultAllowedProcessingModes.getOrElse(AllowedProcessingModes.All),
      componentId.name,
    )
  }

  private def baseComponent(
      componentId: ComponentId,
      icon: String,
      componentGroupName: ComponentGroupName,
      categories: List[String]
  )(implicit loggedUser: LoggedUser): ComponentListElement = {
    val designerWideComponentId = bid(componentId)
    val docsLinks               = if (componentId.name == BuiltInComponentId.Filter.name) List(filterDocsLink) else Nil
    val links                   = docsLinks ++ createLinks(designerWideComponentId, componentId)
    ComponentListElement(
      designerWideComponentId,
      componentId.name,
      icon,
      componentId.`type`,
      componentGroupName,
      categories,
      links,
      0,
      AllowedProcessingModes.All,
      componentId.name
    )
  }

  private def createLinks(
      determineDesignerWideId: DesignerWideComponentId,
      componentId: ComponentId
  )(implicit loggedUser: LoggedUser): List[ComponentLink] =
    linkConfigs
      .filter(_.isAvailable(componentId.`type`, loggedUser))
      .map(_.toComponentLink(determineDesignerWideId, componentId.name))

  private def componentCount(determineDesignerWideId: DesignerWideComponentId, user: LoggedUser) = {
    val sourceComponentId = DesignerWideComponentId(SharedSourceName)
    val sinkComponentId   = DesignerWideComponentId(SharedSinkName)

    determineDesignerWideId match {
      // Order is matter, first should be condition with more number of categories
      case _ @id if id == sourceComponentId && hasAccess(user, CategoryFraud, CategoryMarketing) => 2
      case _ @id if id == sinkComponentId && hasAccess(user, CategoryFraud, CategoryMarketing)   => 2

      case _ @id if id == sourceComponentId && hasAccess(user, CategoryFraud) => 1
      case _ @id if id == sinkComponentId && hasAccess(user, CategoryFraud)   => 1

      case _ @id if id == sourceComponentId && hasAccess(user, CategoryMarketing) => 1
      case _ @id if id == sinkComponentId && hasAccess(user, CategoryMarketing)   => 1

      case _ => 0
    }
  }

  private def hasAccess(user: LoggedUser, categories: String*): Boolean =
    categories.forall(cat => user.can(cat, Permission.Read))

  private lazy val admin = TestFactory.adminUser()

  private val marketingUser = RealLoggedUser(
    id = "1",
    username = "marketingUser",
    categoryPermissions = Map(CategoryMarketing -> Set(Permission.Read))
  )

  private val fraudUser = RealLoggedUser(
    id = "1",
    username = "fraudUser",
    categoryPermissions = Map(CategoryFraud -> Set(Permission.Read)),
    globalPermissions = List(invokePermission)
  )

  private val providerComponents =
    new DynamicComponentProvider()
      .create(ConfigFactory.empty, ProcessObjectDependencies.withConfig(ConfigFactory.empty()))

  private val modelDataMap: Map[ProcessingType, (ModelData, String)] = Map(
    ProcessingTypeStreaming -> (
      LocalModelData(
        streamingConfig,
        providerComponents,
        ComponentMarketingTestConfigCreator,
        determineDesignerWideId = DesignerWideComponentId.default(ProcessingTypeStreaming, _)
      ),
      CategoryMarketing
    ),
    ProcessingTypeFraud -> (
      LocalModelData(
        fraudConfig,
        providerComponents,
        ComponentFraudTestConfigCreator,
        determineDesignerWideId = DesignerWideComponentId.default(ProcessingTypeFraud, _)
      ),
      CategoryFraud
    )
  )

  private val fragmentFromCategories = modelDataMap.toList
    .map { case (processingType, (_, category)) =>
      createFragmentEntity(name = category, category = category, processingType = processingType)
    }

  private val processes        = List(MarketingProcess, FraudProcess, ArchivedFraudProcess)
  private val componentService = prepareService(modelDataMap, processes, fragmentFromCategories)

  def filterUserComponents(user: LoggedUser, categories: List[String]): List[ComponentListElement] =
    prepareComponents(user)
      .collect {
        case component if categories.intersect(component.categories).nonEmpty =>
          component.copy(categories = categories)
      }

  private val expectedAdminComponents     = prepareComponents(admin)
  private val expectedMarketingComponents = filterUserComponents(marketingUser, List(CategoryMarketing))
  private val expectedFraudComponents     = filterUserComponents(fraudUser, List(CategoryFraud))

  private val testingData = Table(
    ("user", "expectedComponents", "possibleCategories"),
    (marketingUser, expectedMarketingComponents, List(CategoryMarketing)),
    (fraudUser, expectedFraudComponents, List(CategoryFraud)),
    (admin, expectedAdminComponents, AllCategories)
  )

  it should "return expected components for all query options" in {
    val queryOptionsList = Table(
      "query options",
      FetchAllWithUsages,
      FetchNonFragmentsWithUsages,
      FetchAllWithoutUsages,
      FetchNonFragmentsWithoutUsages
    )

    forAll(testingData) { (user: LoggedUser, expectedComponents: List[ComponentListElement], _) =>
      forAll(queryOptionsList) { (queryOptions: ComponentListQueryOptions) =>
        val returnedComponents =
          componentService.getComponentsList(queryOptions)(user).futureValue

        queryOptions match {
          case FetchNonFragmentsWithUsages | FetchNonFragmentsWithoutUsages =>
            returnedComponents.size should be < expectedComponents.size
          case FetchAllWithUsages | FetchAllWithoutUsages =>
            returnedComponents.size shouldBe expectedComponents.size
        }

        val filteredExpectedComponents = expectedComponents
          .filter(component =>
            queryOptions match {
              case FetchNonFragmentsWithUsages | FetchNonFragmentsWithoutUsages =>
                component.componentType != ComponentType.Fragment
              case FetchAllWithUsages | FetchAllWithoutUsages =>
                true
            }
          )

        returnedComponents.map(_.id) should contain theSameElementsAs filteredExpectedComponents.map(_.id)
      }
    }
  }

  it should "return components with correct counts for each user" in {

    forAll(testingData) {
      (user: LoggedUser, expectedComponents: List[ComponentListElement], possibleCategories: List[String]) =>
        val returnedComponentsWithUsages =
          componentService.getComponentsList(FetchAllWithUsages)(user).futureValue

        def counts(list: List[ComponentListElement]) = list.map(el => el.id -> el.usageCount).toMap
        val returnedCounts                           = counts(returnedComponentsWithUsages)
        val expectedCounts                           = counts(expectedComponents)
        returnedCounts should contain theSameElementsAs expectedCounts

        forAll(Table("returnedComponents", returnedComponentsWithUsages: _*)) { returnedComponent =>
          checkLinks(returnedComponent, user)

          // Components should contain only user categories
          (returnedComponent.categories diff possibleCategories) shouldBe empty

          val expectedComponent = expectedComponents.find(_.id == returnedComponent.id).value
          returnedComponent shouldEqual expectedComponent
        }
    }
  }

  private def checkLinks(returnedComponent: ComponentListElement, loggedUser: LoggedUser): Unit = {
    // See linksConfig
    val availableLinksId = returnedComponent.componentId match {
      case ComponentId(Service, _) =>
        List(usagesLinkId, editLinkId) ++ List(invokeLinkId).filter(_ => hasPermission(loggedUser, invokePermission))
      case ComponentId(CustomComponent, _) => List(usagesLinkId, editLinkId)
      case ComponentId(BuiltIn, _)         => List(usagesLinkId, filterLinkId)
      case _                               => List(usagesLinkId)
    }

    val availableDocsLinksId = returnedComponent.componentId match {
      case BuiltInComponentId.Filter => List(filterDocsLink.id)
      case _                         => Nil
    }

    // Base components from providers contain more links because of documentation
    returnedComponent.links.map(_.id) should contain theSameElementsAs availableDocsLinksId ++ availableLinksId

    returnedComponent.links
      .filter(l => availableLinksId.contains(l.id))
      .foreach(link => {
        link.title should include(returnedComponent.name)
        link.url.toString should include(returnedComponent.id.value)
      })
  }

  it should "throws exception when components are wrong configured" in {
    import pl.touk.nussknacker.ui.definition.component.WrongConfigurationAttribute._
    val badModelDataMap = Map(
      ProcessingTypeStreaming -> (
        LocalModelData(
          streamingConfig,
          providerComponents,
          ComponentMarketingTestConfigCreator,
          determineDesignerWideId = DesignerWideComponentId.default(ProcessingTypeStreaming, _)
        ),
        CategoryMarketing
      ),
      ProcessingTypeFraud -> (
        LocalModelData(
          wrongConfig,
          providerComponents,
          WronglyConfiguredConfigCreator,
          determineDesignerWideId = DesignerWideComponentId.default(ProcessingTypeFraud, _)
        ),
        CategoryFraud
      )
    )

    val componentService = prepareService(badModelDataMap, List.empty, List.empty)

    val expectedWrongConfigurations = List(
      ComponentWrongConfiguration(
        bid(BuiltInComponentId.Filter),
        IconAttribute,
        List(overriddenIcon, DefaultsComponentIcon.forBuiltInComponent(BuiltInComponentId.Filter))
      ),
      ComponentWrongConfiguration(sharedSourceComponentId, NameAttribute, List(SharedSourceName, SharedSourceV2Name)),
      ComponentWrongConfiguration(
        sharedSourceComponentId,
        IconAttribute,
        List(DefaultsComponentIcon.forNotBuiltInComponentType((Source, None)), overriddenIcon)
      ),
      ComponentWrongConfiguration(
        sharedSourceComponentId,
        ComponentGroupNameAttribute,
        List(SourcesGroupName, executionGroupName)
      ),
      ComponentWrongConfiguration(
        sharedEnricherComponentId,
        IconAttribute,
        List(overriddenIcon, DefaultsComponentIcon.forNotBuiltInComponentType((Service, Some(false))))
      ),
      ComponentWrongConfiguration(
        sharedEnricherComponentId,
        ComponentGroupNameAttribute,
        List(EnrichersGroupName, ServicesGroupName)
      ),
      ComponentWrongConfiguration(
        sharedProvidedComponentId,
        IconAttribute,
        List(DefaultsComponentIcon.forNotBuiltInComponentType((Service, Some(false))), overriddenIcon)
      ),
      ComponentWrongConfiguration(
        sharedProvidedComponentId,
        ComponentGroupNameAttribute,
        List(executionGroupName, overriddenGroupName)
      )
    )
    inside {
      intercept[TestFailedException] {
        componentService.getComponentsList(FetchAllWithUsages)(admin).futureValue
      }.cause
    } { case Some(ComponentConfigurationException(_, wrongConfigurations)) =>
      wrongConfigurations.toList should contain theSameElementsAs expectedWrongConfigurations
    }

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

    val fraudNotSharedSourceComponentId      = cid(ProcessingTypeFraud, ComponentId(Source, NotSharedSourceName))
    val fraudCustomerDataEnricherComponentId = cid(ProcessingTypeFraud, ComponentId(Service, CustomerDataEnricherName))
    val sharedSourceComponentId = DesignerWideComponentId(SharedSourceName) // it's shared id - merged at configs file
    val fragmentComponentId     = cid(ProcessingTypeFraud, ComponentId(Fragment, FraudFragmentName.value))
    val filterComponentId       = bid(BuiltInComponentId.Filter)

    val componentService = prepareService(modelDataMap, processes, List(FraudFragment))

    val testingData = Table(
      ("user", "componentId", "expected"),
      (admin, fragmentComponentId, List((FraudProcessWithFragment, List(ScenarioUsageData(FraudFragmentName.value))))),
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
          determineDesignerWideId: DesignerWideComponentId,
          expected: List[(ScenarioWithDetailsEntity[_], List[NodeUsageData])]
      ) =>
        val result = componentService
          .getComponentUsages(determineDesignerWideId)(user)
          .futureValue
          .map(_.map(n => n.copy(nodesUsagesData = n.nodesUsagesData.sorted)))
        val componentProcesses = expected.map { case (process, nodesUsagesData) =>
          DefaultComponentService.toComponentUsagesInScenario(process, nodesUsagesData.sorted)
        }
        result shouldBe Right(componentProcesses)
    }
  }

  it should "return return error when component doesn't exist" in {
    val componentService    = prepareService(modelDataMap, List.empty, List.empty)
    val notExistComponentId = DesignerWideComponentId("not-exist")
    val result              = componentService.getComponentUsages(notExistComponentId)(admin).futureValue
    result shouldBe Left(ComponentNotFoundError(notExistComponentId))
  }

  private def prepareService(
      modelDataMap: Map[ProcessingType, (ModelData, String)],
      scenarios: List[ScenarioWithDetailsEntity[ScenarioGraph]],
      fragments: List[ScenarioWithDetailsEntity[ScenarioGraph]]
  ): ComponentService = {
    val processingTypeDataProvider = prepareProcessingTypeDataProvider(modelDataMap)
    val processService             = createDbProcessService(scenarios, processingTypeDataProvider)
    new DefaultComponentService(
      componentLinksConfig,
      processingTypeDataProvider,
      processService,
      new DefaultFragmentRepository(MockFetchingProcessRepository.withProcessesDetails(fragments))
    )
  }

  private def prepareProcessingTypeDataProvider(
      modelDataMap: Map[ProcessingType, (ModelData, String)]
  ): ProcessingTypeDataProvider[ComponentServiceProcessingTypeData, ScenarioParametersService] = {
    val processingTypeDataMap: Map[ProcessingType, ProcessingTypeData] = modelDataMap.transform {
      case (processingType, (modelData, category)) =>
        ProcessingTypeData.createProcessingTypeData(
          processingType,
          modelData,
          new MockManagerProvider,
          SchedulingForProcessingType.NotAvailable,
          TestFactory.deploymentManagerDependencies,
          EngineSetupName("Mock"),
          deploymentConfig = ConfigFactory.empty(),
          category = category,
          ComponentDefinitionExtractionMode.FinalDefinition
        )
    }

    ProcessingTypeDataProvider(
      processingTypeDataMap.mapValuesNow(ProcessingTypeDataLoader.toValueWithRestriction),
      ScenarioParametersService.createUnsafe(processingTypeDataMap.mapValuesNow(_.scenarioParameters))
    ).mapValues { processingTypeData =>
      val modelDefinitionEnricher = AlignedComponentsDefinitionProvider(
        processingTypeData.designerModelData
      )
      ComponentServiceProcessingTypeData(modelDefinitionEnricher, processingTypeData.category)
    }
  }

  private def createDbProcessService(
      processes: List[ScenarioWithDetailsEntity[ScenarioGraph]],
      scenarioParametersServiceProvider: ProcessingTypeDataProvider[_, ScenarioParametersService],
  ): DBProcessService =
    new DBProcessService(
      scenarioStatusProvider = mock[ScenarioStatusProvider],
      scenarioStatusPresenter = mock[ScenarioStatusPresenter],
      newProcessPreparers = TestFactory.newProcessPreparerByProcessingType,
      scenarioParametersServiceProvider = scenarioParametersServiceProvider,
      processResolverByProcessingType = TestFactory.processResolverByProcessingType,
      dbioRunner = TestFactory.newDummyDBIOActionRunner(),
      fetchingProcessRepository = MockFetchingProcessRepository.withProcessesDetails(processes),
      scenarioActionRepository = TestFactory.newDummyActionRepository(),
      processRepository = TestFactory.newDummyWriteProcessRepository()
    )

  private def cid(processingType: ProcessingType, componentId: ComponentId): DesignerWideComponentId =
    DesignerWideComponentId.default(processingType, componentId)

  private def bid(componentId: ComponentId): DesignerWideComponentId =
    DesignerWideComponentId.forBuiltInComponent(componentId)

  private implicit def ordering: Ordering[NodeUsageData] = (x: NodeUsageData, y: NodeUsageData) => {
    x.nodeId.compareTo(y.nodeId)
  }

  private def createLinkConfig(
      id: String,
      title: String,
      icon: String,
      url: String,
      supportedComponentTypes: Option[List[ComponentType]],
      requiredPermission: Option[GlobalPermission],
  ): ComponentLinkConfig =
    ComponentLinkConfig(id, title, URI.create(icon), URI.create(url), supportedComponentTypes, requiredPermission)

  @tailrec
  private def hasPermission(loggedUser: LoggedUser, permission: GlobalPermission): Boolean = loggedUser match {
    case user: RealLoggedUser =>
      user match {
        case CommonUser(_, _, _, globalPermissions) => globalPermissions.contains(permission)
        case _: AdminUser                           => true
      }
    case ImpersonatedUser(impersonatedUser, _) => hasPermission(impersonatedUser, permission)
  }

}
