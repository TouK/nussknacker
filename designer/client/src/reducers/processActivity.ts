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
