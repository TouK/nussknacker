package pl.touk.nussknacker.ui.api.description

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.utils.domain.ReflectionBasedUtils.findSubclassesOf
import pl.touk.nussknacker.test.utils.domain.migration.MigrationUtils.allSubclassesContainVersionField
import pl.touk.nussknacker.ui.api.description.MigrationApiEndpoints.Dtos.MigrateScenarioRequestDto

class MigrationApiEndpointsSpec extends AnyFlatSpec with Matchers {

  it should "all subclasses of MigrateScenarioRequestDto contain version field" in {
    val subClasses = findSubclassesOf[MigrateScenarioRequestDto](
      classOf[MigrateScenarioRequestDto],
      "pl.touk.nussknacker.ui.api.description"
    )
    val actual   = allSubclassesContainVersionField(subClasses)
    val expected = subClasses.map(clazz => (clazz.getName, true))

    actual shouldBe expected
  }

}
