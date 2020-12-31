import {ActionType, ProcessStateType, StatusTypeType} from "./types"

class ProcessStateUtils {

  public canDeploy = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Deploy)

  public canCancel = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Cancel)

  public isRunning = (state: ProcessStateType): boolean => state?.status.type === StatusTypeType.Running.toString()

}

export default new ProcessStateUtils()
