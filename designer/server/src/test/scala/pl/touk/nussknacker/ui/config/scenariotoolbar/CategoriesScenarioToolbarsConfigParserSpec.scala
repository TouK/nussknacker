package pl.touk.nussknacker.ui.config.scenariotoolbar

import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CategoriesScenarioToolbarsConfigParserSpec extends AnyFlatSpec with Matchers {

  import org.scalatest.prop.TableDrivenPropertyChecks._

  private val defaultTopLeftConfig =
    """[
      |  { type: "tips-panel", hidden: { fragment: true }, additionalParams: { customParam1: "value1" } }
      |]"""

  private val defaultTopRightConfig =
    s"""[
       |  {
       |    type: "process-actions-panel"
       |    title: "Process Actions"
       |    buttons: [
       |      { type: "process-save", icon: "/assets/buttons/save.svg", name: "save", disabled: { archived: true } }
       |      { type: "process-deploy" }
       |      { type: "custom-link", name: "metrics", title: "Metrics for process $$processName", icon: "/assets/buttons/metrics.svg", url: "/metrics/$$processName" }
       |    ]
       |  }
       |]"""

  private val defaultConfig =
    s"""{
       |  topLeft: $defaultTopLeftConfig
       |  topRight: $defaultTopRightConfig
       |}""".stripMargin

  private val category2TopRightConfig =
    """[
      |  {
      |    type: "process-actions-panel"
      |    title: "Process Actions Right"
      |    buttonsVariant: "small"
      |    buttons: [
      |      { type: "process-save" }
      |    ]
      |  }
      |]"""
  private val category2BottomRightConfig =
    """[
      |  { type: "activities-panel" }
      |]"""

  private val category2Config =
    s"""{
       |  uuid: 58f1acff-d864-4d66-9f86-0fa7319f7043
       |  topRight: $category2TopRightConfig
       |  bottomRight: $category2BottomRightConfig
       |}""".stripMargin

  private val scenarioToolbarConfig = CategoriesScenarioToolbarsConfigParser.parse(
    ConfigFactory.parseString(s"""
      |processToolbarConfig {
      |  defaultConfig: $defaultConfig
      |  categoryConfig {
      |    "Category2": $category2Config
      |  }
      |}""".stripMargin)
  )

  it should "properly create scenario toolbar configuration" in {
    val expectedDefaultToolbarConfig = ScenarioToolbarsConfig.parse(ConfigFactory.parseString(defaultConfig))

    val expectedCategory2ToolbarConfig = ScenarioToolbarsConfig.parse(
      ConfigFactory.parseString(
        s"""{
           |  uuid: 58f1acff-d864-4d66-9f86-0fa7319f7043
           |  topLeft: $defaultTopLeftConfig
           |  topRight: $category2TopRightConfig
           |  bottomRight: $category2BottomRightConfig
           |}""".stripMargin
      )
    )

    val testingConfigs = Table(
      ("category", "expected"),
      ("NotExistCategory", expectedDefaultToolbarConfig),
      ("Category2", expectedCategory2ToolbarConfig)
    )

    forAll(testingConfigs) { (category, expected) =>
      scenarioToolbarConfig.getConfig(category) shouldBe expected
    }
  }

  it should "raise exception when configuration is missing" in {
    intercept[com.typesafe.config.ConfigException.Missing] {
      CategoriesScenarioToolbarsConfigParser.parse(ConfigFactory.empty())
    }
  }

}
