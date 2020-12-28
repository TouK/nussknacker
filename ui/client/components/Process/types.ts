/* eslint-disable i18next/no-literal-string */
import {UnknownRecord} from "../../types/common"
import {CustomAction} from "../toolbars/edit/EditPanel";

export enum ActionType {
  Deploy = "DEPLOY",
  Cancel = "CANCEL",
  Pause = "PAUSE",
}

export enum StatusType {
  Running = "RunningStateStatus",
  NotDeployed= "AllowDeployStateStatus"
}

export enum StatusName {
  Running = "RUNNING",
  NotDeployed = "NOT_DEPLOYED",
}

export type ProcessVersionId = number

export type ProcessActionType = {
  performedAt: Date,
  user: string,
  action: ActionType,
  commentId?: number,
  comment?: string,
  buildInfo?: UnknownRecord,
  processVersionId: ProcessVersionId,
}

export type ProcessVersionType = {
  createDate: Date,
  user: string,
  actions: Array<ProcessActionType>,
  modelVersion: number,
  processVersionId: ProcessVersionId,
}

export interface ProcessType {
  id: string,
  name: string,
  processId: number,
  processVersionId: number,
  isArchived: boolean,
  isSubprocess: boolean,
  isLatestVersion: boolean,
  processCategory: string,
  processType: string,
  modificationDate: number,
  createdAt: Date,
  createdBy: string,
  lastAction?: ProcessActionType,
  lastDeployedAction?: ProcessActionType,
  state: ProcessStateType,
  history?: ProcessVersionType[],
}

export type ProcessStateType = {
  status: {
    name: StatusName,
    type: StatusType,
  },
  deploymentId?: string,
  allowedActions: Array<ActionType>,
  customActions: Array<CustomAction>,
  icon?: string,
  tooltip?: string,
  description?: string,
  startTime?: Date,
  attributes?: UnknownRecord,
  errors?: Array<string>,
}
