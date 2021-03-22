/* eslint-disable i18next/no-literal-string */
import {UnknownRecord} from "../../types/common"

export enum ActionType {
  Deploy = "DEPLOY",
  Cancel = "CANCEL",
  Archive = "ARCHIVE",
  UnArchive = "UNARCHIVE",
  Pause = "PAUSE",
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
  status: StatusType,
  deploymentId?: string,
  allowedActions: Array<ActionType>,
  icon?: string,
  tooltip?: string,
  description?: string,
  startTime?: Date,
  attributes?: UnknownRecord,
  errors?: Array<string>,
}

export type StatusType = {
  name: string,
  type: string,
}
