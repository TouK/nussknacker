import {Action} from "../actions/reduxTypes"

export type Attachment = {
  processId: $TodoType,
  processVersionId: $TodoType,
  id: $TodoType,
  createDate: $TodoType,
  user: $TodoType,
  fileName: string,
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
