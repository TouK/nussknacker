import {ActionType, StatusType, ProcessState, Process} from "../ProcessTypes"

class ProcessStateUtils {

  UNKNOWN_ICON = "/assets/states/status-unknown.svg"

  public isRunning = (state: ProcessState) => this.getStateStatus(state) === StatusType.Running.toString()

  public isDeployed = (process: Process) => process?.lastAction?.action === ActionType.Deploy

  private getStateStatus = (state: ProcessState) => {
    const status = state.status.value

    if (status === null) {
      return StatusType.Unknown.toString()
    }

    return status.toUpperCase()
  }
}

export default new ProcessStateUtils()
