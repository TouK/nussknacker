import {ActionType, ProcessStateType} from "./types"

class ProcessStateUtils {

  public canDeploy = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Deploy)

  public canCancel = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Cancel)

  public canArchive = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Archive)

}

export default new ProcessStateUtils()
