import {Action} from "../actions/reduxTypes"
import {Instant} from "../types/common";

export type User = string

export type Attachment = {
  processId: $TodoType,
  processVersionId: $TodoType,
  id: $TodoType,
  createDate: Instant,
  user: User,
  fileName: string,
}

export type Comment = {
  id: number,
  processId: string
  processVersionId: string,
  user: User,
  content: string,
  createDate: Instant,
}


export type ProcessActivityState = {
  comments: $TodoType[],
  attachments: Attachment[],
}

const emptyProcessActivity: ProcessActivityState = {
  comments: [],
  attachments: [],
}

export function reducer(state: ProcessActivityState = emptyProcessActivity, action: Action): ProcessActivityState {
  switch (action.type) {
    case "DISPLAY_PROCESS_ACTIVITY": {
      return {
        ...state,
        comments: action.comments,
        attachments: action.attachments || [],
      }
    }
    default:
      return state
  }
}
