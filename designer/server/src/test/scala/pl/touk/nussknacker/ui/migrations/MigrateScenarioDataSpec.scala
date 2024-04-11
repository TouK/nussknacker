package pl.touk.nussknacker.ui.migrations

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.utils.domain.ReflectionBasedUtils.findSubclassesOf
import pl.touk.nussknacker.test.utils.domain.migration.MigrationUtils.allSubclassesContainVersionField

class MigrateScenarioDataSpec extends AnyFreeSpecLike with Matchers {

  "all subclasses of MigrateScenarioRequest" - {
    "contain version field of type Int" in {
      val subClasses = findSubclassesOf[MigrateScenarioData](
        classOf[MigrateScenarioData],
        "pl.touk.nussknacker.ui.migrations"
      )
      val actual   = allSubclassesContainVersionField[MigrateScenarioData](subClasses)
      val expected = subClasses.map(clazz => (clazz.getName, true))

      actual shouldBe expected
    }

  }

}
