package pl.touk.nussknacker.ui.process

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.test.utils.domain.ProcessTestData

class NewProcessPreparerSpec extends AnyFlatSpec with Matchers {

  it should "create new empty process" in {
    val preparer = new NewProcessPreparer(ProcessTestData.streamingTypeSpecificInitialData, Map.empty)

    val emptyProcess = preparer.prepareEmptyProcess(ProcessName("processId1"), isFragment = false)

    emptyProcess.name shouldBe ProcessName("processId1")
    emptyProcess.nodes shouldBe List.empty
  }

}
