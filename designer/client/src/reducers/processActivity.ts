import { Action } from "../actions/reduxTypes";
import { Instant } from "../types/common";
import { ProcessVersionId } from "../components/Process/types";
import { UIActivity } from "../components/toolbars/activities";

export type User = string;

export type Attachment = {
    processVersionId: ProcessVersionId;
    id: string;
    createDate: Instant;
    user: User;
    fileName: string;
};

export type Comment = {
    id: number;
    processVersionId: string;
    user: User;
    content: string;
    createDate: Instant;
};

export type ProcessActivityState = {
    comments: $TodoType[];
    attachments: Attachment[];
    activities: UIActivity[];
};

const emptyProcessActivity: ProcessActivityState = {
    comments: [],
    attachments: [],
    activities: [],
};

export function reducer(state: ProcessActivityState = emptyProcessActivity, action: Action): ProcessActivityState {
    switch (action.type) {
        case "DISPLAY_PROCESS_ACTIVITY": {
            return {
                ...state,
                comments: action.comments,
                attachments: action.attachments || [],
            };
        }
        case "GET_SCENARIO_ACTIVITIES": {
            return {
                ...state,
                activities: action.activities,
            };
        }
        case "UPDATE_SCENARIO_ACTIVITIES": {
            return {
                ...state,
                activities: action.activities,
            };
        }
        default:
            return state;
    }
}
