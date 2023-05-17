package pl.touk.nussknacker.ui.initialization

import org.scalatest.tags.Slow
import pl.touk.nussknacker.ui.api.helpers._

@Slow
class InitializationOnPostgresItSpec extends InitializationOnDbItSpec with WithPostgresDbTesting
