package pl.touk.nussknacker.ui.migrations

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.utils.domain.ReflectionBasedUtils.findSubclassesOf
import pl.touk.nussknacker.test.utils.domain.migration.MigrationUtils.allSubclassesContainVersionField

class MigrateScenarioRequestSpec extends AnyFlatSpec with Matchers {

  it should "all subclasses of MigrateScenarioRequest contain version field" in {
    val subClasses = findSubclassesOf[MigrateScenarioRequest](
      classOf[MigrateScenarioRequest],
      "pl.touk.nussknacker.ui.migrations"
    )
    val actual   = allSubclassesContainVersionField(subClasses)
    val expected = subClasses.map(clazz => (clazz.getName, true))

    actual shouldBe expected
  }

}
