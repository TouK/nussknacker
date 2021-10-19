package pl.touk.nussknacker.ui.component

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, ComponentType}
import pl.touk.nussknacker.engine.api.deployment.DeploymentManager
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.management.FlinkStreamingDeploymentManagerProvider
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeData}
import pl.touk.nussknacker.restmodel.component.{ComponentAction, ComponentListElement}
import pl.touk.nussknacker.ui.api.helpers.TestFactory.MockDeploymentManager
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessingTypes}
import pl.touk.nussknacker.ui.process.ConfigProcessCategoryService
import pl.touk.nussknacker.ui.process.processingtypedata.MapBasedProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessRepository}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import java.util.UUID

class DefaultComponentServiceSpec extends FlatSpec with Matchers {

  import ComponentsTestsData._
  import DefaultsComponentGroupName._
  import DefaultsComponentIcon._
  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.engine.api.component.ComponentType._

  private val ExecutionGroupName: ComponentGroupName = ComponentGroupName("execution")
  private val ResponseGroupName: ComponentGroupName = ComponentGroupName("response")
  private val OverriddenIcon = "OverriddenIcon.svg"

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

  private object MockManagerProvider extends FlinkStreamingDeploymentManagerProvider {
    override def createDeploymentManager(modelData: ModelData, config: Config): DeploymentManager = new MockDeploymentManager
  }

  private object MarketingProcessingTypeData
    extends ProcessingTypeData(new MockDeploymentManager, LocalModelData(streamingConfig, ComponentMarketingTestConfigCreator), MockManagerProvider.typeSpecificDataInitializer, None, false) {
    override def hashCode(): Int = 1
  }

  private object FraudProcessingTypeData
    extends ProcessingTypeData(new MockDeploymentManager, LocalModelData(fraudConfig, ComponentFraudTestConfigCreator), MockManagerProvider.typeSpecificDataInitializer, None, false) {
    override def hashCode(): Int = 2
  }

  private def streamingComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], actions: List[ComponentAction], usageCount: Int) = {
    val uuid = ComponentListElement.createComponentUUID(MarketingProcessingTypeData.hashCode(), name, componentType)
    ComponentListElement(uuid, name, icon, componentType, componentGroupName, categories, actions, usageCount)
  }

  private def fraudComponent(name: String, icon: String, componentType: ComponentType, componentGroupName: ComponentGroupName, categories: List[String], actions: List[ComponentAction], usageCount: Int) = {
    val uuid = ComponentListElement.createComponentUUID(FraudProcessingTypeData.hashCode(), name, componentType)
    ComponentListElement(uuid, name, icon, componentType, componentGroupName, categories, actions, usageCount)
  }

  private def baseComponent(componentType: ComponentType, icon: String, componentGroupName: ComponentGroupName, categories: List[String], actions: List[ComponentAction], usageCount: Int) = {
    val uuid = UUID.nameUUIDFromBytes(componentType.toString.getBytes)
    ComponentListElement(uuid, componentType.toString, icon, componentType, componentGroupName, categories, actions, usageCount)
  }

  val baseComponents: List[ComponentListElement] = List(
    baseComponent(Filter, OverriddenIcon, BaseGroupName, allCategories, List.empty, 0),
    baseComponent(Split, SplitIcon, BaseGroupName, allCategories, List.empty, 0),
    baseComponent(Switch, SwitchIcon, BaseGroupName, allCategories, List.empty, 0),
    baseComponent(Variable, VariableIcon, BaseGroupName, allCategories, List.empty, 0),
    baseComponent(MapVariable, MapVariableIcon, BaseGroupName, allCategories, List.empty, 0),
    //baseComponent(FragmentInput, FragmentInputIcon, FragmentsGroupName, allCategories, List.empty, 0),
    //baseComponent(FragmentOutput, FragmentOutputIcon, FragmentsGroupName, allCategories, List.empty, 0),
  )

  val availableMarketingComponents: List[ComponentListElement] = List(
    streamingComponent("customStream", CustomNodeIcon, CustomNode, CustomGroupName, marketingWithoutSuperCategories, List.empty, 0),
    streamingComponent("customerDataEnricher", OverriddenIcon, Enricher, ResponseGroupName, List(categoryMarketing), List.empty, 0),
    //ComponentListElement(DynamicProvidedComponent.Name, ProcessorIcon, Processor, ExecutionGroupName, List(categoryMarketingTests), List.empty, 0),
    streamingComponent("emptySource", SourceIcon, Source, SourcesGroupName, List(categoryMarketing), List.empty, 0),
    streamingComponent("fuseBlockService", ProcessorIcon, Processor, ExecutionGroupName, marketingWithoutSuperCategories, List.empty, 0),
    streamingComponent("monitor", SinkIcon, Sink, ExecutionGroupName, marketingAllCategories, List.empty, 0),
    streamingComponent("optionalCustomStream", CustomNodeIcon, CustomNode, OptionalEndingCustomGroupName, marketingWithoutSuperCategories, List.empty, 0),
    streamingComponent("sendEmail", SinkIcon, Sink, ExecutionGroupName, List(categoryMarketing), List.empty, 0),
    streamingComponent("superSource", SourceIcon, Source, SourcesGroupName, List(categoryMarketingSuper), List.empty, 0),
  )

  val availableFraudComponents: List[ComponentListElement] = List(
    fraudComponent("customStream", CustomNodeIcon, CustomNode, CustomGroupName, fraudWithoutSupperCategories, List.empty, 0),
    fraudComponent("customerDataEnricher", EnricherIcon, Enricher, EnrichersGroupName, List(categoryFraud), List.empty, 0),
    fraudComponent("emptySource", SourceIcon, Source, SourcesGroupName, fraudAllCategories, List.empty, 0),
    fraudComponent("fuseBlockService", ProcessorIcon, Processor, ServicesGroupName, fraudWithoutSupperCategories, List.empty, 0),
    fraudComponent("optionalCustomStream", CustomNodeIcon, CustomNode, OptionalEndingCustomGroupName, fraudWithoutSupperCategories, List.empty, 0),
    fraudComponent("secondMonitor", SinkIcon, Sink, ExecutionGroupName, fraudAllCategories, List.empty, 0),
    fraudComponent("sendEmail", SinkIcon, Sink, ExecutionGroupName, fraudWithoutSupperCategories, List.empty, 0),
  )

  val subprocessMarketingComponents: List[ComponentListElement] = marketingAllCategories.map(cat => {
    val uuid = ComponentListElement.createComponentUUID(MarketingProcessingTypeData.hashCode(), cat, ComponentType.Fragments)
    val icon = DefaultsComponentIcon.fromComponentType(ComponentType.Fragments)
    ComponentListElement(uuid, cat, icon, ComponentType.Fragments, FragmentsGroupName, List(cat), Nil, 0)
  })

  val subprocessFraudComponents: List[ComponentListElement] = marketingAllCategories.map(cat => {
    val uuid = ComponentListElement.createComponentUUID(FraudProcessingTypeData.hashCode(), cat, ComponentType.Fragments)
    val icon = if (cat == categoryFraud) OverriddenIcon else DefaultsComponentIcon.fromComponentType(ComponentType.Fragments)
    ComponentListElement(uuid, cat, icon, ComponentType.Fragments, FragmentsGroupName, List(cat), Nil, 0)
  })

  private val availableComponents: List[ComponentListElement] = (
    baseComponents ++ availableMarketingComponents ++ availableFraudComponents ++ subprocessMarketingComponents ++ subprocessFraudComponents
  ).sortBy(ComponentListElement.sortMethod)

  it should "return components for each user" in {
    val processingTypeDataProvider = TestFactory.mapProcessingTypeDataProvider(
      TestProcessingTypes.Streaming -> MarketingProcessingTypeData,
      TestProcessingTypes.Fraud -> FraudProcessingTypeData,
    )

    val stubSubprocessRepository = new SubprocessRepository {
      override def loadSubprocesses(versions: Map[String, Long]): Set[SubprocessDetails] = allCategories.map(cat =>
        SubprocessDetails(CanonicalProcess(MetaData(cat, FragmentSpecificData()), ExceptionHandlerRef(List()), Nil, Nil), cat)
      ).toSet
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

    defaultComponentService.getComponentsList(marketingFullUser) shouldBe marketingFullComponents

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
      components shouldBe expectedComponents

      //Components should contain only user categories
      val componentsCategories = components.flatMap(_.categories).distinct.sorted
      componentsCategories.diff(possibleCategories).isEmpty shouldBe true
    }
  }

  private def preparePermissions(categories: List[String]) =
    categories.map(c => c -> Set(Permission.Read)).toMap
}
