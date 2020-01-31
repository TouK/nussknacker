import {ActionType, StatusType, ProcessStateType, ProcessType} from "../ProcessTypes"

class ProcessStateUtils {

  UNKNOWN_ICON = "/assets/states/status-unknown.svg"

  public isStateRunning = (state: ProcessStateType) => this.getStateStatus(state) === StatusType.Running.toString()

  public isDeployed = (process: ProcessType) => process?.lastAction?.action === ActionType.Deploy

  public isProcessRunning = (process: ProcessType) => this.isStateRunning(process.state)

  private getStateStatus = (state: ProcessStateType) => {
    const status = state?.status.name

    if (status == null) {
      return StatusType.Unknown.toString()
    }

    return status.toUpperCase()
  }
}

export default new ProcessStateUtils()
