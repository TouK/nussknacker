package pl.touk.nussknacker.ui.component

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component._
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.test.PatientScalaFutures

class DefaultComponentIdProviderSpec extends AnyFlatSpec with Matchers with PatientScalaFutures {

  import org.scalatest.prop.TableDrivenPropertyChecks._
  import pl.touk.nussknacker.engine.spel.Implicits._

  private val componentNameToOverride = "componentNameToOverride"
  private val processingType          = "testProcessingType"
  private val componentName           = "testComponentName"

  private val overriddenId = ComponentId("overriddenId")

  private val componentIdProvider = new DefaultComponentIdProvider({
    case (_, ComponentInfo(_, `componentNameToOverride`)) =>
      Some(SingleComponentConfig.zero.copy(componentId = Some(overriddenId)))
    case _ =>
      None
  })

  private val notBuiltIntComponents = (ComponentType.values - ComponentType.BuiltIn).toList
    .map(ComponentInfo(_, componentName))

  it should "create ComponentId" in {
    val testingData = Table(
      ("componentsInfo", "expectedComponentId"),
      (BuiltInComponentInfo.All, BuiltInComponentInfo.All.map(cid)),
      (notBuiltIntComponents, notBuiltIntComponents.map(cid)),
      (
        notBuiltIntComponents
          .filter(_.`type` != ComponentType.Fragment)
          .map(_.copy(name = componentNameToOverride)),
        notBuiltIntComponents.filter(_.`type` != ComponentType.Fragment).map(_ => overriddenId)
      ),
    )

    forAll(testingData) { (componentInfo: List[ComponentInfo], expected: List[ComponentId]) =>
      val result = componentInfo.map(componentIdProvider.createComponentId(processingType, _))
      result shouldBe expected
    }
  }

  it should "throw exception when forbidden overriding is detected" in {
    val componentsWithRestrictedType =
      BuiltInComponentInfo.All ++ List(ComponentInfo(ComponentType.Fragment, componentName))

    val provider = new DefaultComponentIdProvider({ (_, _) =>
      Some(SingleComponentConfig.zero.copy(componentId = Some(overriddenId)))
    })

    componentsWithRestrictedType.foreach(componentInfo => {
      intercept[IllegalArgumentException] {
        provider.createComponentId(processingType, componentInfo)
      }.getMessage shouldBe s"Component id can't be overridden for: $componentInfo"
    })
  }

  private def cid(componentInfo: ComponentInfo): ComponentId =
    ComponentId.default(processingType, componentInfo)
}
