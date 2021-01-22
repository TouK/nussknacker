import {ActionType, ProcessStateType, StatusName, StatusType} from "./types"

class ProcessStateUtils {

  public canDeploy = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Deploy)

  public canCancel = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Cancel)

  public isRunning = (state: ProcessStateType): boolean => state?.status.type === StatusType.Running.toString()

  //FIXME: It's just fast fix for support periodic running status. Remove it after when we will properly support actions.
  private static readonly PeriodicRunningStatusesNames: Array<StatusName> = [
    StatusName.Scheduled,
    StatusName.WaitingForSchedule,
  ]

  public isPeriodicRunning = (state: ProcessStateType): boolean => ProcessStateUtils.PeriodicRunningStatusesNames.includes(state?.status.name)

}

export default new ProcessStateUtils()
