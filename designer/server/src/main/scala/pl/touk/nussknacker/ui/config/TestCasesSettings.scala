package pl.touk.nussknacker.ui.config

final case class TestCasesSettings(
    multipleEnabled: Boolean = false,
    multipleParallelismExecution: Int = 2,
)
