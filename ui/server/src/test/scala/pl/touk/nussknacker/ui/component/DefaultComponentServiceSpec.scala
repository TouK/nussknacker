package pl.touk.nussknacker.ui.component

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentId}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeData}
import pl.touk.nussknacker.restmodel.component.{ComponentListElement, ComponentAction}
import pl.touk.nussknacker.ui.api.helpers.TestFactory.MockDeploymentManager
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessRepository}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

class DefaultComponentServiceSpec extends FlatSpec with Matchers {

  import ComponentModelData._
  import DefaultsComponentGroupName._
  import DefaultsComponentIcon._
  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.engine.api.component.ComponentType._

  private val ExecutionGroupName: ComponentGroupName = ComponentGroupName("execution")
  private val ResponseGroupName: ComponentGroupName = ComponentGroupName("response")
  private val OverriddenIcon = "OverriddenIcon.svg"

  private val actions = Map(
    "list" -> ActionDef("Component usages", "/components/$processName/processes", "/assets/icons/$componentName/list.ico"),
    "invoke" -> ActionDef("Invoke component $componentName", "/components/$componentId/processes", "/assets/icons/invoke.ico", Some(List(Enricher, Processor))),
  )

  private val actionsStr = actions.map { case(id, action) =>
    s"""$id {
       | name: "${action.title}",
       | url: "${action.url}",
       | icon: "${action.icon}",
       | ${action.types.map(types => s"""types: [${types.mkString(",")}]""").getOrElse("")}
       | }""".stripMargin
  }.mkString(",")

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
      |  componentsAction {
      |    $actionsStr
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

  private case class ActionDef(title: String, url: String, icon: String, types: Option[List[ComponentType]] = None) {
    def toComponentAction(actionId: String, componentId: String, componentName: String): ComponentAction = {
      def replaceStr(str: String): String = str.replaceAll("$componentId", componentId).replaceAll("$componentName", componentName)
      ComponentAction(actionId, replaceStr(title), replaceStr(url), replaceStr(icon))
    }
  }

  private def createActions(componentId: String, componentName: String, componentType: ComponentType): List[ComponentAction] =
    actions
      .filter(el => el._2.types.isEmpty || el._2.types.contains(componentType))
      .map{ case (id, action) => action.toComponentAction(id, componentId, componentName) }
      .toList

  private object MockManagerProvider extends FlinkStreamingDeploymentManagerProvider {
    override def createDeploymentManager(modelData: ModelData, config: Config): DeploymentManager = new MockDeploymentManager
  }

  private def streamingComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String]) = {
    val id = ComponentId(TestProcessingTypes.Streaming, name, componentType)
    val actions = createActions(id, name, componentType)
    ComponentListElement(id, name, icon, componentType, componentGroupName, categories, actions)
  }

  private def fraudComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String]) = {
    val id = ComponentId(TestProcessingTypes.Fraud, name, componentType)
    val actions = createActions(id, name, componentType)
    ComponentListElement(id, name, icon, componentType, componentGroupName, categories)
  }

  private def baseComponent(componentType: ComponentType, icon: String, componentGroupName: ComponentGroupName, categories: List[String]) = {
    val id = ComponentId.create(componentType.toString)
    val actions = createActions(componentType.toString, componentType.toString, componentType)
    ComponentListElement(id, componentType.toString, icon, componentType, componentGroupName, categories, actions)
  }

  private val baseComponents: List[ComponentListElement] = List(
    baseComponent(Filter, OverriddenIcon, BaseGroupName, allCategories),
    baseComponent(Split, SplitIcon, BaseGroupName, allCategories),
    baseComponent(Switch, SwitchIcon, BaseGroupName, allCategories),
    baseComponent(Variable, VariableIcon, BaseGroupName, allCategories),
    baseComponent(MapVariable, MapVariableIcon, BaseGroupName, allCategories),
    //baseComponent(FragmentInput, FragmentInputIcon, FragmentsGroupName, allCategories),
    //baseComponent(FragmentOutput, FragmentOutputIcon, FragmentsGroupName, allCategories),
  )

  private val availableMarketingComponents: List[ComponentListElement] = List(
    streamingComponent("customStream", CustomNodeIcon, CustomNode, CustomGroupName, marketingWithoutSuperCategories),
    streamingComponent("customerDataEnricher", OverriddenIcon, Enricher, ResponseGroupName, List(categoryMarketing)),
    streamingComponent(DynamicProvidedComponent.Name, ProcessorIcon, Processor, ExecutionGroupName, List(categoryMarketingTests)),
    streamingComponent("emptySource", SourceIcon, Source, SourcesGroupName, List(categoryMarketing)),
    streamingComponent("fuseBlockService", ProcessorIcon, Processor, ExecutionGroupName, marketingWithoutSuperCategories),
    streamingComponent("monitor", SinkIcon, Sink, ExecutionGroupName, marketingAllCategories),
    streamingComponent("optionalCustomStream", CustomNodeIcon, CustomNode, OptionalEndingCustomGroupName, marketingWithoutSuperCategories),
    streamingComponent("sendEmail", SinkIcon, Sink, ExecutionGroupName, List(categoryMarketing)),
    streamingComponent("superSource", SourceIcon, Source, SourcesGroupName, List(categoryMarketingSuper)),
  )

  private val availableFraudComponents: List[ComponentListElement] = List(
    fraudComponent("customStream", CustomNodeIcon, CustomNode, CustomGroupName, fraudWithoutSupperCategories),
    fraudComponent("customerDataEnricher", EnricherIcon, Enricher, EnrichersGroupName, List(categoryFraud)),
    fraudComponent("emptySource", SourceIcon, Source, SourcesGroupName, fraudAllCategories),
    fraudComponent("fuseBlockService", ProcessorIcon, Processor, ServicesGroupName, fraudWithoutSupperCategories),
    fraudComponent("optionalCustomStream", CustomNodeIcon, CustomNode, OptionalEndingCustomGroupName, fraudWithoutSupperCategories),
    fraudComponent("secondMonitor", SinkIcon, Sink, ExecutionGroupName, fraudAllCategories),
    fraudComponent("sendEmail", SinkIcon, Sink, ExecutionGroupName, fraudWithoutSupperCategories),
  )

  private val subprocessMarketingComponents: List[ComponentListElement] = marketingAllCategories.map(cat => {
    val id = ComponentId(TestProcessingTypes.Streaming, cat, Fragments)
    val icon = DefaultsComponentIcon.fromComponentType(Fragments)
    val actions = createActions(id, cat, Fragments)
    ComponentListElement(id, cat, icon, Fragments, FragmentsGroupName, List(cat), actions)
  })

  private val subprocessFraudComponents: List[ComponentListElement] = fraudAllCategories.map(cat => {
    val id = ComponentId(TestProcessingTypes.Fraud, cat, Fragments)
    val icon = if (cat == categoryFraud) OverriddenIcon else DefaultsComponentIcon.fromComponentType(Fragments)
    val actions = createActions(id, cat, Fragments)
    ComponentListElement(id, cat, icon, Fragments, FragmentsGroupName, List(cat), actions)
  })

  private val availableComponents: List[ComponentListElement] = (
    baseComponents ++ availableMarketingComponents ++ availableFraudComponents ++ subprocessMarketingComponents ++ subprocessFraudComponents
  ).sortBy(ComponentListElement.sortMethod)

  it should "return components for each user" in {
    val processingTypeDataProvider = new MapBasedProcessingTypeDataProvider(Map(
      TestProcessingTypes.Streaming -> LocalModelData(streamingConfig, ComponentMarketingTestConfigCreator),
      TestProcessingTypes.Fraud -> LocalModelData(fraudConfig, ComponentFraudTestConfigCreator),
    ).map{ case (processingType, config) =>
      processingType -> ProcessingTypeData(new MockDeploymentManager, config, MockManagerProvider.typeSpecificDataInitializer, None, supportsSignals = false)
    })

    val stubSubprocessRepository = new SubprocessRepository {
      override def loadSubprocesses(versions: Map[String, Long]): Set[SubprocessDetails] = allCategories.map(cat => {
        val metaData = MetaData(cat, FragmentSpecificData())
        val exceptionHandler = ExceptionHandlerRef(List())
        val node = FlatNode(SubprocessInputDefinition(cat, Nil, None))
        SubprocessDetails(CanonicalProcess(metaData, exceptionHandler, List(node), Nil), cat)
      }).toSet
    }

    val categoryService = new ConfigProcessCategoryService(categoryConfig)
    val defaultComponentService = new DefaultComponentService(processingTypeDataProvider, stubSubprocessRepository, categoryService)

    val admin = TestFactory.adminUser()
    val marketingFullUser = TestFactory.user(permissions = preparePermissions(marketingWithoutSuperCategories))
    val marketingTestsUser = TestFactory.user(username = categoryFraudTests, permissions = preparePermissions(List(categoryMarketingTests)))
    val fraudFullUser = TestFactory.user(permissions = preparePermissions(fraudWithoutSupperCategories))
    val fraudTestsUser = TestFactory.user(permissions = preparePermissions(List(categoryFraudTests)))

    def filterComponents(categories: List[String]): List[ComponentListElement] =
      availableComponents
        .map(c => c -> categories.intersect(c.categories))
        .filter(seq => seq._2.nonEmpty)
        .map(seq => seq._1.copy(categories = seq._2))
        .sortBy(ComponentListElement.sortMethod)

    val marketingFullComponents = filterComponents(marketingWithoutSuperCategories)
    val marketingTestsComponents = filterComponents(List(categoryMarketingTests))
    val fraudFullComponents = filterComponents(fraudWithoutSupperCategories)
    val fraudTestsComponents = filterComponents(List(categoryFraudTests))

    val testingData = Table(
      ("user", "expectedComponents", "possibleCategories"),
      (admin, availableComponents, allCategories),
      (marketingFullUser, marketingFullComponents, marketingWithoutSuperCategories),
      (marketingTestsUser, marketingTestsComponents, List(categoryMarketingTests)),
      (fraudFullUser, fraudFullComponents, fraudWithoutSupperCategories),
      (fraudTestsUser, fraudTestsComponents, List(categoryFraudTests)),
    )

    forAll(testingData) { (user: LoggedUser, expectedComponents: List[ComponentListElement], possibleCategories: List[String]) =>
      val components = defaultComponentService.getComponentsList(user)
      //we don't do exact matching, to avoid handling autoLoaded components here
      components should contain allElementsOf expectedComponents

      //Components should contain only user categories
      val componentsCategories = components.flatMap(_.categories).distinct.sorted
      componentsCategories.diff(possibleCategories).isEmpty shouldBe true
    }
  }

  private def preparePermissions(categories: List[String]) =
    categories.map(c => c -> Set(Permission.Read)).toMap
}
