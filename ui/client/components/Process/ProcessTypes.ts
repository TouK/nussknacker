
export enum ActionType {
  Deploy = "DEPLOY",
  Cancel = "CANCEL"
}

export enum StatusType {
  Running = "RUNNING",
  Unknown = "UNKNOWN"
}

export type ProcessAction = {
  performedAt: Date;
  user: string;
  action: ActionType;
  commentId?: number;
  comment?: string;
  buildInfo?: {};
}

export interface Process {
  id: number;
  name: string;
  processVersionId: number;
  isArchived: boolean;
  isSubprocess: boolean;
  processCategory: string;
  processType: string;
  modificationDate: number;
  createdAt: Date;
  createdBy: string;
  lastAction?: ProcessAction;
  lastDeployedAction?: ProcessAction;
  state: ProcessState;
}

export type ListProcess  = {
  [P in keyof Process]?: Process[P];
}

export type ProcessDetails = {
  [P in keyof Process]?: Process[P];
} & {
  json: {};
}

export type ProcessState = {
  status: {
    value: string;
  };
  deploymentId?: string;
  allowedActions: Array<ActionType>;
  icon?: string;
  tooltip?: string;
  startTime?: Date;
  attributes?: {};
  errorMessage?: string;
}
