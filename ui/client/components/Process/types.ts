/* eslint-disable i18next/no-literal-string */
export enum ActionType {
  Deploy = "DEPLOY",
  Cancel = "CANCEL",
  Pause = "PAUSE",
}

export enum StatusType {
  Running = "RunningStateStatus",
}

export type ProcessActionType = {
  performedAt: Date,
  user: string,
  action: ActionType,
  commentId?: number,
  comment?: string,
  buildInfo?: {},
}

export type ProcessVersionType = {
  createDate: Date,
  user: string,
  actions: Array<ProcessActionType>
  modelVersion: number,
  processVersionId: number,
}

export interface ProcessType {
  id: string,
  name: string,
  processId: number,
  processVersionId: number,
  isArchived: boolean,
  isSubprocess: boolean,
  processCategory: string,
  processType: string,
  modificationDate: number,
  createdAt: Date,
  createdBy: string,
  lastAction?: ProcessActionType,
  lastDeployedAction?: ProcessActionType,
  state: ProcessStateType,
}

export type ProcessStateType = {
  status: {
    name: string,
    type: StatusType,
  },
  deploymentId?: string,
  allowedActions: Array<ActionType>,
  icon?: string,
  tooltip?: string,
  description?: string,
  startTime?: Date,
  attributes?: {},
  errors?: Array<string>,
}
