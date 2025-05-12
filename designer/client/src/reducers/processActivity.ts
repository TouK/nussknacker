import type { Action } from "../actions/reduxTypes";
import type { ProcessVersionId } from "../components/Process/types";
import type { UIActivity } from "../components/toolbars/activities";
import type { Instant } from "../types/common";

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
    activities: UIActivity[];
};

const emptyProcessActivity: ProcessActivityState = {
    activities: [],
};

export function reducer(state: ProcessActivityState = emptyProcessActivity, action: Action): ProcessActivityState {
    switch (action.type) {
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
