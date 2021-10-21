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
import pl.touk.nussknacker.restmodel.component.{ComponentAction, ComponentListElement}
import pl.touk.nussknacker.ui.api.helpers.TestFactory.MockDeploymentManager
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessingTypes}
import pl.touk.nussknacker.ui.config.ComponentActionConfig
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessRepository}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

class DefaultComponentServiceSpec extends FlatSpec with Matchers {

  import ComponentModelData._
  import DefaultsComponentGroupName._
  import DefaultsComponentIcon._
  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.restmodel.component.ComponentAction._
  import pl.touk.nussknacker.restmodel.component.ComponentType._

  private val ExecutionGroupName: ComponentGroupName = ComponentGroupName("execution")
  private val ResponseGroupName: ComponentGroupName = ComponentGroupName("response")
  private val OverriddenIcon = "OverriddenIcon.svg"

  private val actionsConfig = Map(
    "usages" -> ComponentActionConfig(s"Usages of $ComponentNameTemplate", s"/assets/components/actions/usages.svg", None, None),
    "invoke" -> ComponentActionConfig(s"Invoke component $ComponentNameTemplate", s"/assets/components/actions/invoke.svg", Some(s"/components/$ComponentIdTemplate/Invoke"), Some(List(Enricher, Processor))),
    "edit" -> ComponentActionConfig(s"Edit component $ComponentNameTemplate", "/assets/components/actions/edit.svg", Some(s"/components/$ComponentIdTemplate/"), Some(List(CustomNode, Enricher, Processor))),
    "filter" -> ComponentActionConfig(s"Custom action $ComponentNameTemplate", "/assets/components/actions/filter.svg", Some(s"/components/$ComponentIdTemplate/filter"), Some(List(Filter))),
  )

  private val globalConfig = ConfigFactory.parseString(
    s"""
      componentsAction {
        ${actionsConfig.map { case(id, action) =>
      s"""$id {
         | title: "${action.title}",
         | ${action.url.map(url => s"""url: "$url",""").getOrElse("")}
         | icon: "${action.icon}",
         | ${action.types.map(types => s"""types: [${types.mkString(",")}]""").getOrElse("")}
         | }""".stripMargin
          }.toList.mkString(",\n")}
      }
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
      |    },
      |    switch {
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

  private object TestType extends Enumeration {
    type TestType = Value
    val all: Value = Value("all")
    val fraud: Value = Value("fraud")
    val marketing: Value = Value("marketing")
  }

  private def createActions(componentId: String, componentName: String, componentType: ComponentType): List[ComponentAction] =
    actionsConfig
      .filter{ case (_, action) => action.isAvailable(componentType) }
      .map{ case (id, action) =>
        ComponentAction(id, action.title, action.icon, componentId, componentName, action.url)
      }
      .toList
      .sortBy(_.id)

  private object MockManagerProvider extends FlinkStreamingDeploymentManagerProvider {
    override def createDeploymentManager(modelData: ModelData, config: Config): DeploymentManager = new MockDeploymentManager
  }

  private def marketingComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String]) = {
    val id = ComponentId(TestProcessingTypes.Streaming, name, componentType)
    val actions = createActions(id, name, componentType)
    ComponentListElement(id, name, icon, componentType, componentGroupName, categories, actions)
  }

  private def fraudComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String]) = {
    val id = ComponentId(TestProcessingTypes.Fraud, name, componentType)
    val actions = createActions(id, name, componentType)
    ComponentListElement(id, name, icon, componentType, componentGroupName, categories, actions)
  }

  private def baseComponent(componentType: ComponentType, icon: String, componentGroupName: ComponentGroupName, categories: List[String]) = {
    val id = ComponentId.create(componentType.toString)
    val actions = createActions(componentType.toString, componentType.toString, componentType)
    ComponentListElement(id, componentType.toString, icon, componentType, componentGroupName, categories, actions)
  }

  private def createBaseComponents(`type`: TestType.TestType): List[ComponentListElement] = {
    val switchIcon = if(`type` == TestType.fraud) OverriddenIcon else SwitchIcon

    List(
      baseComponent(Filter, OverriddenIcon, BaseGroupName, allCategories),
      baseComponent(Split, SplitIcon, BaseGroupName, allCategories),
      baseComponent(Switch, switchIcon, BaseGroupName, allCategories),
      baseComponent(Variable, VariableIcon, BaseGroupName, allCategories),
      baseComponent(MapVariable, MapVariableIcon, BaseGroupName, allCategories),
    )
  }

  private val availableMarketingComponents: List[ComponentListElement] = List(
    marketingComponent("customStream", CustomNodeIcon, CustomNode, CustomGroupName, marketingWithoutSuperCategories),
    marketingComponent("customerDataEnricher", OverriddenIcon, Enricher, ResponseGroupName, List(categoryMarketing)),
    marketingComponent(DynamicProvidedComponent.Name, ProcessorIcon, Processor, ExecutionGroupName, List(categoryMarketingTests)),
    marketingComponent("emptySource", SourceIcon, Source, SourcesGroupName, List(categoryMarketing)),
    marketingComponent("fuseBlockService", ProcessorIcon, Processor, ExecutionGroupName, marketingWithoutSuperCategories),
    marketingComponent("monitor", SinkIcon, Sink, ExecutionGroupName, marketingAllCategories),
    marketingComponent("optionalCustomStream", CustomNodeIcon, CustomNode, OptionalEndingCustomGroupName, marketingWithoutSuperCategories),
    marketingComponent("sendEmail", SinkIcon, Sink, ExecutionGroupName, List(categoryMarketing)),
    marketingComponent("superSource", SourceIcon, Source, SourcesGroupName, List(categoryMarketingSuper)),
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

  private def createAvailableComponents(`type`: TestType.TestType): List[ComponentListElement] = (
    createBaseComponents(`type`) ++ availableMarketingComponents ++ availableFraudComponents ++ subprocessMarketingComponents ++ subprocessFraudComponents
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
    val defaultComponentService = new DefaultComponentService(globalConfig, processingTypeDataProvider, stubSubprocessRepository, categoryService)

    val admin = TestFactory.adminUser()
    val marketingFullUser = TestFactory.user(permissions = preparePermissions(marketingWithoutSuperCategories))
    val marketingTestsUser = TestFactory.user(username = categoryFraudTests, permissions = preparePermissions(List(categoryMarketingTests)))
    val fraudFullUser = TestFactory.user(permissions = preparePermissions(fraudWithoutSupperCategories))
    val fraudTestsUser = TestFactory.user(permissions = preparePermissions(List(categoryFraudTests)))

    def filterComponents(`type`: TestType.TestType, categories: List[String]): List[ComponentListElement] =
      createAvailableComponents(`type`)
        .map(c => c -> categories.intersect(c.categories))
        .filter(seq => seq._2.nonEmpty)
        .map(seq => seq._1.copy(categories = seq._2))
        .sortBy(ComponentListElement.sortMethod)

    val adminComponents = createAvailableComponents(TestType.all)
    val marketingFullComponents = filterComponents(TestType.marketing, marketingWithoutSuperCategories)
    val marketingTestsComponents = filterComponents(TestType.marketing, List(categoryMarketingTests))
    val fraudFullComponents = filterComponents(TestType.fraud, fraudWithoutSupperCategories)
    val fraudTestsComponents = filterComponents(TestType.fraud, List(categoryFraudTests))

    val testingData = Table(
      ("user", "expectedComponents", "possibleCategories"),
      (admin, adminComponents, allCategories),
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

      components.foreach(comp => {
        //See actionsConfig
        val actionsCount = comp.componentType match {
          case Processor | Enricher => 3
          case CustomNode => 2
          case Filter => 2
          case _ => 1
        }
        comp.actions.size shouldBe actionsCount

        comp.actions.foreach(action => {
          action.title should include (comp.name)
          action.url.foreach(u => u should include (comp.id))
        })
      })
    }
  }

  private def preparePermissions(categories: List[String]) =
    categories.map(c => c -> Set(Permission.Read)).toMap
}
