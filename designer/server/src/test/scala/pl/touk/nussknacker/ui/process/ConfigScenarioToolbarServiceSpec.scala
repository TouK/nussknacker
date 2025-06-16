package pl.touk.nussknacker.ui.process

import com.typesafe.config.ConfigFactory
import io.circe.{parser, Json}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.util.UriUtils
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil
import pl.touk.nussknacker.ui.config.scenariotoolbar._
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.security.api.CommonUser

class ConfigScenarioToolbarServiceSpec extends AnyFlatSpec with Matchers {

  import org.scalatest.prop.TableDrivenPropertyChecks._

  private lazy val parsedConfig = CategoriesScenarioToolbarsConfigParser.parse(
    ConfigFactory.parseString(
      """{
        |  processToolbarConfig {
        |    defaultConfig {
        |      topLeft: [
        |        { type: "tips-panel", hidden: {fragment: true} }
        |      ]
        |      topRight: [
        |        {
        |          type: "process-actions-panel"
        |          title: "Process Actions $processName"
        |          buttons: [
        |            { type: "process-save", icon: "/assets/$processId/buttons/save.svg", title: "save", disabled: {archived: true} }
        |            { type: "custom-link", name: "metrics", title: "metrics for process", url: "/metrics/$processName" }
        |            { type: "custom-link", name: "analytics", url: "/analytics/$processId", disabled: {archived: true, fragment: true, type: "allof"} }
        |          ]
        |        }
        |        {
        |           id: "buttons1"
        |           type: "buttons-panel"
        |           hidden: {fragment: false}
        |           buttonsVariant: "small"
        |           buttons: [ { type: "process-deploy" }, { type: "process-pdf", hidden: {archived: true} }]
        |        }
        |        {
        |           id: "buttons2"
        |           type: "buttons-panel"
        |           hidden: { archived: true }
        |           buttons: [ { type: "process-cancel" } ]
        |        }
        |      ]
        |    }
        |    categoryConfig {
        |      "Category1" {
        |        topLeft: [
        |          { type: "creator-panel", hidden: {fragment: true, archived: false, type: "allof"} }
        |          { type: "activities-panel" }
        |        ]
        |        bottomRight: []
        |      }
        |    }
        |  }
        |}""".stripMargin
    )
  )

  private val service = new ConfigScenarioToolbarService(parsedConfig)

  it should "verify all toolbar condition cases" in {
    val process          = createProcess("process", "Category1", isFragment = false, isArchived = false)
    val archivedProcess  = createProcess("archived-process", "Category1", isFragment = false, isArchived = true)
    val fragment         = createProcess("fragment", "Category1", isFragment = true, isArchived = false)
    val archivedFragment = createProcess("archived-fragment", "Category1", isFragment = true, isArchived = true)

    val testingData = Table(
      ("process", "condition", "expected"),
      (process, None, false),
      (archivedProcess, None, false),
      (fragment, None, false),
      (archivedFragment, None, false),

      // All of conditions match
      (process, Some(ToolbarCondition(Some(false), None, None, None, Some(ToolbarConditionType.AllOf))), true),
      (process, Some(ToolbarCondition(Some(true), None, None, None, Some(ToolbarConditionType.AllOf))), false),
      (process, Some(ToolbarCondition(None, Some(false), None, None, Some(ToolbarConditionType.AllOf))), true),
      (process, Some(ToolbarCondition(None, Some(true), None, None, Some(ToolbarConditionType.AllOf))), false),
      (process, Some(ToolbarCondition(Some(false), Some(false), None, None, Some(ToolbarConditionType.AllOf))), true),
      (process, Some(ToolbarCondition(Some(false), Some(true), None, None, Some(ToolbarConditionType.AllOf))), false),
      (process, Some(ToolbarCondition(Some(true), Some(false), None, None, Some(ToolbarConditionType.AllOf))), false),
      (process, Some(ToolbarCondition(Some(true), Some(true), None, None, Some(ToolbarConditionType.AllOf))), false),
      (archivedProcess, Some(ToolbarCondition(Some(false), None, None, None, Some(ToolbarConditionType.AllOf))), true),
      (archivedProcess, Some(ToolbarCondition(Some(true), None, None, None, Some(ToolbarConditionType.AllOf))), false),
      (archivedProcess, Some(ToolbarCondition(None, Some(false), None, None, Some(ToolbarConditionType.AllOf))), false),
      (archivedProcess, Some(ToolbarCondition(None, Some(true), None, None, Some(ToolbarConditionType.AllOf))), true),
      (
        archivedProcess,
        Some(ToolbarCondition(Some(false), Some(false), None, None, Some(ToolbarConditionType.AllOf))),
        false
      ),
      (
        archivedProcess,
        Some(ToolbarCondition(Some(false), Some(true), None, None, Some(ToolbarConditionType.AllOf))),
        true
      ),
      (
        archivedProcess,
        Some(ToolbarCondition(Some(true), Some(false), None, None, Some(ToolbarConditionType.AllOf))),
        false
      ),
      (
        archivedProcess,
        Some(ToolbarCondition(Some(true), Some(true), None, None, Some(ToolbarConditionType.AllOf))),
        false
      ),
      (fragment, Some(ToolbarCondition(Some(false), None, None, None, Some(ToolbarConditionType.AllOf))), false),
      (fragment, Some(ToolbarCondition(Some(true), None, None, None, Some(ToolbarConditionType.AllOf))), true),
      (fragment, Some(ToolbarCondition(None, Some(false), None, None, Some(ToolbarConditionType.AllOf))), true),
      (fragment, Some(ToolbarCondition(None, Some(true), None, None, Some(ToolbarConditionType.AllOf))), false),
      (fragment, Some(ToolbarCondition(Some(false), Some(false), None, None, Some(ToolbarConditionType.AllOf))), false),
      (fragment, Some(ToolbarCondition(Some(false), Some(true), None, None, Some(ToolbarConditionType.AllOf))), false),
      (fragment, Some(ToolbarCondition(Some(true), Some(false), None, None, Some(ToolbarConditionType.AllOf))), true),
      (fragment, Some(ToolbarCondition(Some(true), Some(true), None, None, Some(ToolbarConditionType.AllOf))), false),
      (
        archivedFragment,
        Some(ToolbarCondition(Some(false), None, None, None, Some(ToolbarConditionType.AllOf))),
        false
      ),
      (archivedFragment, Some(ToolbarCondition(Some(true), None, None, None, Some(ToolbarConditionType.AllOf))), true),
      (
        archivedFragment,
        Some(ToolbarCondition(None, Some(false), None, None, Some(ToolbarConditionType.AllOf))),
        false
      ),
      (archivedFragment, Some(ToolbarCondition(None, Some(true), None, None, Some(ToolbarConditionType.AllOf))), true),
      (
        archivedFragment,
        Some(ToolbarCondition(Some(false), Some(false), None, None, Some(ToolbarConditionType.AllOf))),
        false
      ),
      (
        archivedFragment,
        Some(ToolbarCondition(Some(false), Some(true), None, None, Some(ToolbarConditionType.AllOf))),
        false
      ),
      (
        archivedFragment,
        Some(ToolbarCondition(Some(true), Some(false), None, None, Some(ToolbarConditionType.AllOf))),
        false
      ),
      (
        archivedFragment,
        Some(ToolbarCondition(Some(true), Some(true), None, None, Some(ToolbarConditionType.AllOf))),
        true
      ),
      // "always" condition
      (
        process,
        Some(
          ToolbarCondition(fragment = Some(true), None, always = Some(true), None, Some(ToolbarConditionType.AllOf))
        ),
        false
      ),
      (
        process,
        Some(
          ToolbarCondition(fragment = Some(true), None, always = Some(false), None, Some(ToolbarConditionType.AllOf))
        ),
        false
      ),
      (
        fragment,
        Some(
          ToolbarCondition(fragment = Some(true), None, always = Some(true), None, Some(ToolbarConditionType.AllOf))
        ),
        true
      ),
      (
        fragment,
        Some(
          ToolbarCondition(fragment = Some(true), None, always = Some(false), None, Some(ToolbarConditionType.AllOf))
        ),
        false
      ),
      // "oneOfUserRoles" condition
      (
        process,
        Some(
          ToolbarCondition(
            None,
            None,
            None,
            oneOfUserRoles = Some(Set("alfa", "beta")),
            Some(ToolbarConditionType.AllOf)
          )
        ),
        true
      ),
      (
        process,
        Some(ToolbarCondition(None, None, None, oneOfUserRoles = Some(Set("alfa")), Some(ToolbarConditionType.AllOf))),
        true
      ),
      (
        process,
        Some(ToolbarCondition(None, None, None, oneOfUserRoles = Some(Set("beta")), Some(ToolbarConditionType.AllOf))),
        false
      ),

      // One of conditions match
      (process, Some(ToolbarCondition(Some(false), None, None, None, Some(ToolbarConditionType.OneOf))), true),
      (process, Some(ToolbarCondition(Some(true), None, None, None, Some(ToolbarConditionType.OneOf))), false),
      (process, Some(ToolbarCondition(None, Some(false), None, None, Some(ToolbarConditionType.OneOf))), true),
      (process, Some(ToolbarCondition(None, Some(true), None, None, Some(ToolbarConditionType.OneOf))), false),
      (process, Some(ToolbarCondition(Some(false), Some(false), None, None, Some(ToolbarConditionType.OneOf))), true),
      (process, Some(ToolbarCondition(Some(false), Some(true), None, None, Some(ToolbarConditionType.OneOf))), true),
      (process, Some(ToolbarCondition(Some(true), Some(false), None, None, Some(ToolbarConditionType.OneOf))), true),
      (process, Some(ToolbarCondition(Some(true), Some(true), None, None, Some(ToolbarConditionType.OneOf))), false),
      (archivedProcess, Some(ToolbarCondition(Some(false), None, None, None, Some(ToolbarConditionType.OneOf))), true),
      (archivedProcess, Some(ToolbarCondition(Some(true), None, None, None, Some(ToolbarConditionType.OneOf))), false),
      (archivedProcess, Some(ToolbarCondition(None, Some(false), None, None, Some(ToolbarConditionType.OneOf))), false),
      (archivedProcess, Some(ToolbarCondition(None, Some(true), None, None, Some(ToolbarConditionType.OneOf))), true),
      (
        archivedProcess,
        Some(ToolbarCondition(Some(false), Some(false), None, None, Some(ToolbarConditionType.OneOf))),
        true
      ),
      (
        archivedProcess,
        Some(ToolbarCondition(Some(false), Some(true), None, None, Some(ToolbarConditionType.OneOf))),
        true
      ),
      (
        archivedProcess,
        Some(ToolbarCondition(Some(true), Some(false), None, None, Some(ToolbarConditionType.OneOf))),
        false
      ),
      (
        archivedProcess,
        Some(ToolbarCondition(Some(true), Some(true), None, None, Some(ToolbarConditionType.OneOf))),
        true
      ),
      (fragment, Some(ToolbarCondition(Some(false), None, None, None, Some(ToolbarConditionType.OneOf))), false),
      (fragment, Some(ToolbarCondition(Some(true), None, None, None, Some(ToolbarConditionType.OneOf))), true),
      (fragment, Some(ToolbarCondition(None, Some(false), None, None, Some(ToolbarConditionType.OneOf))), true),
      (fragment, Some(ToolbarCondition(None, Some(true), None, None, Some(ToolbarConditionType.OneOf))), false),
      (fragment, Some(ToolbarCondition(Some(false), Some(false), None, None, Some(ToolbarConditionType.OneOf))), true),
      (fragment, Some(ToolbarCondition(Some(false), Some(true), None, None, Some(ToolbarConditionType.OneOf))), false),
      (fragment, Some(ToolbarCondition(Some(true), Some(false), None, None, Some(ToolbarConditionType.OneOf))), true),
      (fragment, Some(ToolbarCondition(Some(true), Some(true), None, None, Some(ToolbarConditionType.OneOf))), true),
      (
        archivedFragment,
        Some(ToolbarCondition(Some(false), None, None, None, Some(ToolbarConditionType.OneOf))),
        false
      ),
      (archivedFragment, Some(ToolbarCondition(Some(true), None, None, None, Some(ToolbarConditionType.OneOf))), true),
      (
        archivedFragment,
        Some(ToolbarCondition(None, Some(false), None, None, Some(ToolbarConditionType.OneOf))),
        false
      ),
      (archivedFragment, Some(ToolbarCondition(None, Some(true), None, None, Some(ToolbarConditionType.OneOf))), true),
      (
        archivedFragment,
        Some(ToolbarCondition(Some(false), Some(false), None, None, Some(ToolbarConditionType.OneOf))),
        false
      ),
      (
        archivedFragment,
        Some(ToolbarCondition(Some(false), Some(true), None, None, Some(ToolbarConditionType.OneOf))),
        true
      ),
      (
        archivedFragment,
        Some(ToolbarCondition(Some(true), Some(false), None, None, Some(ToolbarConditionType.OneOf))),
        true
      ),
      (
        archivedFragment,
        Some(ToolbarCondition(Some(true), Some(true), None, None, Some(ToolbarConditionType.OneOf))),
        true
      ),
      // "always" condition
      (process, Some(ToolbarCondition(None, None, always = Some(true), None, Some(ToolbarConditionType.OneOf))), true),
      (
        process,
        Some(ToolbarCondition(None, None, always = Some(false), None, Some(ToolbarConditionType.OneOf))),
        false
      ),
      // "oneOfUserRoles" condition
      (
        process,
        Some(
          ToolbarCondition(
            None,
            None,
            None,
            oneOfUserRoles = Some(Set("alfa", "beta")),
            Some(ToolbarConditionType.OneOf)
          )
        ),
        true
      ),
      (
        process,
        Some(ToolbarCondition(None, None, None, oneOfUserRoles = Some(Set("alfa")), Some(ToolbarConditionType.OneOf))),
        true
      ),
      (
        process,
        Some(ToolbarCondition(None, None, None, oneOfUserRoles = Some(Set("beta")), Some(ToolbarConditionType.OneOf))),
        false
      ),
      (
        fragment,
        Some(
          ToolbarCondition(
            fragment = Some(true),
            None,
            None,
            oneOfUserRoles = Some(Set("beta")),
            Some(ToolbarConditionType.OneOf)
          )
        ),
        true
      ),
    )

    val user = CommonUser(
      id = "testUser",
      username = "testUser",
      categoryPermissions = Map.empty,
      globalPermissions = List.empty,
      roles = Set("alfa")
    )
    forAll(testingData) {
      (process: ScenarioWithDetailsEntity[_], condition: Option[ToolbarCondition], expected: Boolean) =>
        val result = ToolbarHelper.verifyCondition(condition, process, user)
        result shouldBe expected
    }
  }

  it should "properly create process toolbar configuration" in {
    val process          = createProcess("process with space", "Category1", isFragment = false, isArchived = false)
    val archivedProcess  = createProcess("archived-process", "Category1", isFragment = false, isArchived = true)
    val fragment         = createProcess("fragment", "Category1", isFragment = true, isArchived = false)
    val archivedFragment = createProcess("archived-fragment", "Category1", isFragment = true, isArchived = true)
    val processCategory2 = createProcess("process2", "Category2", isFragment = false, isArchived = false)

    val testingData = Table(
      "process",
      process,
      archivedProcess,
      fragment,
      archivedFragment,
      processCategory2
    )

    implicit val user: CommonUser = CommonUser(
      id = "testUser",
      username = "testUser",
      categoryPermissions = Map.empty,
      globalPermissions = List.empty,
      roles = Set.empty
    )

    forAll(testingData) { (process: ScenarioWithDetailsEntity[_]) =>
      val result   = service.getScenarioToolbarSettings(process).asJson
      val expected = prepareExpectedProcessToolbarSettingsJson(process)
      result shouldBe expected
    }
  }

  private def prepareExpectedProcessToolbarSettingsJson(process: ScenarioWithDetailsEntity[_]): Json = {
    val processToolbarConfig = parsedConfig.getConfig(process.processCategory)
    val id                   = ToolbarHelper.createScenarioToolbarId(processToolbarConfig, process)

    def urlEncodedProcessName(process: ScenarioWithDetailsEntity[_]) = UriUtils.encodeURIComponent(process.name.value)

    (process.isFragment, process.isArchived, process.processCategory) match {
      case (false, false, "Category1") =>
        parser
          .parse(
            s"""{
               |  "id" : "$id",
               |  "topLeft" : [
               |    {
               |      "id" : "creator-panel",
               |      "title" : null,
               |      "buttonsVariant" : null,
               |      "buttons" : null,
               |      "additionalParams" : null
               |    },
               |    {
               |      "id" : "activities-panel",
               |      "title" : null,
               |      "buttonsVariant" : null,
               |      "buttons" : null,
               |      "additionalParams" : null
               |    }
               |  ],
               |  "topRight" : [
               |    {
               |      "id" : "process-actions-panel",
               |      "title" : "Process Actions ${process.name}",
               |      "buttonsVariant" : null,
               |      "buttons" : [
               |        {
               |          "type" : "process-save",
               |          "name" : null,
               |          "title" : "save",
               |          "icon" : "/assets/${process.processId.value}/buttons/save.svg",
               |          "url" : null,
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        },
               |        {
               |          "type" : "custom-link",
               |          "name" : "metrics",
               |          "title" : "metrics for process",
               |          "icon" : null,
               |          "url" : "/metrics/${urlEncodedProcessName(process)}",
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        },
               |        {
               |          "type" : "custom-link",
               |          "name" : "analytics",
               |          "title" : null,
               |          "icon" : null,
               |          "url" : "/analytics/${process.processId.value}",
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        }
               |      ],
               |      "additionalParams" : null
               |    },
               |    {
               |      "id" : "buttons2",
               |      "title" : null,
               |      "buttonsVariant" : null,
               |      "buttons" : [
               |        {
               |          "type" : "process-cancel",
               |          "name" : null,
               |          "title" : null,
               |          "icon" : null,
               |          "url" : null,
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        }
               |      ],
               |      "additionalParams" : null
               |    }
               |  ]
               |}""".stripMargin
          )
          .fold(throw _, identity)
      case (false, true, "Category1") =>
        parser
          .parse(
            s"""{
               |  "id" : "$id",
               |  "topLeft" : [
               |    {
               |      "id" : "creator-panel",
               |      "title" : null,
               |      "buttonsVariant" : null,
               |      "buttons" : null,
               |      "additionalParams" : null
               |    },
               |    {
               |      "id" : "activities-panel",
               |      "title" : null,
               |      "buttonsVariant" : null,
               |      "buttons" : null,
               |      "additionalParams" : null
               |    }
               |  ],
               |  "topRight" : [
               |    {
               |      "id" : "process-actions-panel",
               |      "title" : "Process Actions ${process.name}",
               |      "buttonsVariant" : null,
               |      "buttons" : [
               |        {
               |          "type" : "process-save",
               |          "name" : null,
               |          "title" : "save",
               |          "icon" : "/assets/${process.processId.value}/buttons/save.svg",
               |          "url" : null,
               |          "disabled" : true,
               |          "markdownContent" : null,
               |          "docs" : null
               |        },
               |        {
               |          "type" : "custom-link",
               |          "name" : "metrics",
               |          "title" : "metrics for process",
               |          "icon" : null,
               |          "url" : "/metrics/${urlEncodedProcessName(process)}",
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        },
               |        {
               |          "type" : "custom-link",
               |          "name" : "analytics",
               |          "title" : null,
               |          "icon" : null,
               |          "url" : "/analytics/${process.processId.value}",
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        }
               |      ],
               |      "additionalParams" : null
               |    }
               |  ]
               |}""".stripMargin
          )
          .fold(throw _, identity)
      case (true, false, "Category1") =>
        parser
          .parse(
            s"""{
               |  "id" : "$id",
               |  "topLeft" : [
               |    {
               |      "id" : "activities-panel",
               |      "title" : null,
               |      "buttonsVariant" : null,
               |      "buttons" : null,
               |      "additionalParams" : null
               |    }
               |  ],
               |  "topRight" : [
               |    {
               |      "id" : "process-actions-panel",
               |      "title" : "Process Actions ${process.name}",
               |      "buttonsVariant" : null,
               |      "buttons" : [
               |        {
               |          "type" : "process-save",
               |          "name" : null,
               |          "title" : "save",
               |          "icon" : "/assets/${process.processId.value}/buttons/save.svg",
               |          "url" : null,
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        },
               |        {
               |          "type" : "custom-link",
               |          "name" : "metrics",
               |          "title" : "metrics for process",
               |          "icon" : null,
               |          "url" : "/metrics/${urlEncodedProcessName(process)}",
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        },
               |        {
               |          "type" : "custom-link",
               |          "name" : "analytics",
               |          "title" : null,
               |          "icon" : null,
               |          "url" : "/analytics/${process.processId.value}",
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        }
               |      ],
               |      "additionalParams" : null
               |    },
               |    {
               |      "id" : "buttons1",
               |      "title" : null,
               |      "buttonsVariant" : "small",
               |      "buttons" : [
               |        {
               |          "type" : "process-deploy",
               |          "name" : null,
               |          "title" : null,
               |          "icon" : null,
               |          "url" : null,
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        },
               |        {
               |          "type" : "process-pdf",
               |          "name" : null,
               |          "title" : null,
               |          "icon" : null,
               |          "url" : null,
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        }
               |      ],
               |      "additionalParams" : null
               |    },
               |    {
               |      "id" : "buttons2",
               |      "title" : null,
               |      "buttonsVariant" : null,
               |      "buttons" : [
               |        {
               |          "type" : "process-cancel",
               |          "name" : null,
               |          "title" : null,
               |          "icon" : null,
               |          "url" : null,
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        }
               |      ],
               |      "additionalParams" : null
               |    }
               |  ]
               |}""".stripMargin
          )
          .fold(throw _, identity)
      case (true, true, "Category1") =>
        parser
          .parse(
            s"""{
               |  "id" : "$id",
               |  "topLeft" : [
               |    {
               |      "id" : "creator-panel",
               |      "title" : null,
               |      "buttonsVariant" : null,
               |      "buttons" : null,
               |      "additionalParams" : null
               |    },
               |    {
               |      "id" : "activities-panel",
               |      "title" : null,
               |      "buttonsVariant" : null,
               |      "buttons" : null,
               |      "additionalParams" : null
               |    }
               |  ],
               |  "topRight" : [
               |    {
               |      "id" : "process-actions-panel",
               |      "title" : "Process Actions ${process.name}",
               |      "buttonsVariant" : null,
               |      "buttons" : [
               |        {
               |          "type" : "process-save",
               |          "name" : null,
               |          "title" : "save",
               |          "icon" : "/assets/${process.processId.value}/buttons/save.svg",
               |          "url" : null,
               |          "disabled" : true,
               |          "markdownContent" : null,
               |          "docs" : null
               |        },
               |        {
               |          "type" : "custom-link",
               |          "name" : "metrics",
               |          "title" : "metrics for process",
               |          "icon" : null,
               |          "url" : "/metrics/${urlEncodedProcessName(process)}",
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        },
               |        {
               |          "type" : "custom-link",
               |          "name" : "analytics",
               |          "title" : null,
               |          "icon" : null,
               |          "url" : "/analytics/${process.processId.value}",
               |          "disabled" : true,
               |          "markdownContent" : null,
               |          "docs" : null
               |        }
               |      ],
               |      "additionalParams" : null
               |    },
               |    {
               |      "id" : "buttons1",
               |      "title" : null,
               |      "buttonsVariant" : "small",
               |      "buttons" : [
               |        {
               |          "type" : "process-deploy",
               |          "name" : null,
               |          "title" : null,
               |          "icon" : null,
               |          "url" : null,
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        }
               |      ],
               |      "additionalParams" : null
               |    }
               |  ]
               |}""".stripMargin
          )
          .fold(throw _, identity)
      case (false, false, "Category2") =>
        parser
          .parse(
            s"""{
               |  "id" : "$id",
               |  "topLeft" : [
               |    {
               |      "id" : "tips-panel",
               |      "title" : null,
               |      "buttonsVariant" : null,
               |      "buttons" : null,
               |      "additionalParams" : null
               |    }
               |  ],
               |  "topRight" : [
               |    {
               |      "id" : "process-actions-panel",
               |      "title" : "Process Actions ${process.name}",
               |      "buttonsVariant" : null,
               |      "buttons" : [
               |        {
               |          "type" : "process-save",
               |          "name" : null,
               |          "title" : "save",
               |          "icon" : "/assets/${process.processId.value}/buttons/save.svg",
               |          "url" : null,
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        },
               |        {
               |          "type" : "custom-link",
               |          "name" : "metrics",
               |          "title" : "metrics for process",
               |          "icon" : null,
               |          "url" : "/metrics/${urlEncodedProcessName(process)}",
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        },
               |        {
               |          "type" : "custom-link",
               |          "name" : "analytics",
               |          "title" : null,
               |          "icon" : null,
               |          "url" : "/analytics/${process.processId.value}",
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        }
               |      ],
               |      "additionalParams" : null
               |    },
               |    {
               |      "id" : "buttons2",
               |      "title" : null,
               |      "buttonsVariant" : null,
               |      "buttons" : [
               |        {
               |          "type" : "process-cancel",
               |          "name" : null,
               |          "title" : null,
               |          "icon" : null,
               |          "url" : null,
               |          "disabled" : false,
               |          "markdownContent" : null,
               |          "docs" : null
               |        }
               |      ],
               |      "additionalParams" : null
               |    }
               |  ]
               |}""".stripMargin
          )
          .fold(throw _, identity)
      case other =>
        throw new IllegalStateException(s"Not expected input: $other")
    }
  }

  private def createProcess(name: String, category: String, isFragment: Boolean, isArchived: Boolean) =
    TestProcessUtil.wrapWithScenarioDetailsEntity(
      name = ProcessName(name),
      category = category,
      isFragment = isFragment,
      isArchived = isArchived
    )

}
