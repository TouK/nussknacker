package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{
  CustomActionResult,
  DeploymentData,
  DeploymentId,
  ExternalDeploymentId,
  User
}
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults

// TODO: We should try to unify all commands below to the form:
//       ScenarioCommand[Result](scenarioVersionData, actionInvocationContext, commandSpecificData) where
//        - scenarioDeploymentData - should contain: scenarioMetadata, scenarioVersionData, scenarioGraph, deploymentId
//        - actionInvocationContext - should contain: user invoking current action and other audit data
//       Currently:
//        - ProcessVersion has some part of scenarioMetadata (processName, processId, user (I guess)) and some part of scenarioVersionData (versionId, modelVersion)
//        - scenarioGraph is represented as CanonicalProcess and is laying separately
//        - user is laying separately
//       After we do this, we can consider replacing them by one common case class and only the commandSpecificData will be a sealed trait
sealed trait ScenarioCommand[Result]

/**
  * This command is invoked separately before deploy, to be able to give user quick feedback, as deploy (e.g. on Flink) may take long time
  */
case class ValidateScenarioCommand(
    processVersion: ProcessVersion,
    deploymentData: DeploymentData,
    canonicalProcess: CanonicalProcess
) extends ScenarioCommand[Unit]

/**
  * We assume that validate was already called and was successful, currently savepointPath is flink specific, but we could
  * leverage this concept also for other engines
  */
case class RunDeploymentCommand(
    processVersion: ProcessVersion,
    deploymentData: DeploymentData,
    canonicalProcess: CanonicalProcess,
    savepointPath: Option[String]
) extends ScenarioCommand[Option[ExternalDeploymentId]]

case class CancelDeploymentCommand(scenarioName: ProcessName, deploymentId: DeploymentId, user: User)
    extends ScenarioCommand[Unit]

// TODO: We should merge it with CancelDeploymentCommand
case class StopDeploymentCommand(
    scenarioName: ProcessName,
    deploymentId: DeploymentId,
    savepointDir: Option[String],
    user: User
) extends ScenarioCommand[SavepointResult]

// TODO: Custom is a bad name. We should expose in the name the fact that it is for the purpose of commands that leveraging
//       the power of our "generic" Parameter's concept that allows to change FE side without need to write
//       a dedicated code on the FE side. Not every new command need to be a custom scenario command.
//       We should also describe it in some scaladoc
case class CustomActionCommand(
    actionName: ScenarioActionName,
    processVersion: ProcessVersion,
    canonicalProcess: CanonicalProcess,
    user: User,
    params: Map[String, String]
) extends ScenarioCommand[CustomActionResult]

// TODO Commands below will be legacy in some future because they operate on the scenario level instead of deployment level -
//      we should replace them by commands operating on deployment
case class TestScenarioCommand(
    scenarioName: ProcessName,
    canonicalProcess: CanonicalProcess,
    scenarioTestData: ScenarioTestData
) extends ScenarioCommand[TestResults]

case class MakeScenarioSavepointCommand(scenarioName: ProcessName, savepointDir: Option[String])
    extends ScenarioCommand[SavepointResult]

case class CancelScenarioCommand(scenarioName: ProcessName, user: User) extends ScenarioCommand[Unit]

case class StopScenarioCommand(scenarioName: ProcessName, savepointDir: Option[String], user: User)
    extends ScenarioCommand[SavepointResult]
