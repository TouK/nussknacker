import HttpService from "../../http/HttpService";
import { displayProcessActivity } from "./displayProcessActivity";
import { ThunkAction } from "../reduxTypes";
import { ProcessName } from "../../components/Process/types";

export function addComment(processName: ProcessName, processVersionId, comment): ThunkAction {
    return (dispatch) => {
        return HttpService.addComment(processName, processVersionId, comment).then(() => {
            return dispatch(displayProcessActivity(processName));
        });
    };
}

export function deleteComment(processName: ProcessName, commentId): ThunkAction {
    return (dispatch) => {
        return HttpService.deleteComment(processName, commentId).then(() => {
            return dispatch(displayProcessActivity(processName));
        });
    };
}
