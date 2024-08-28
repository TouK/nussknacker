//Developers can handle error on two ways:
//1. Catching it at HttpService and show error information modal - application still works normally
//2. Catching it at Containers / etc.. and show ErrorPage by run action handleHTTPError - application stops working
import { ErrorType } from "../../reducers/httpErrorHandler";

export interface HandleHTTPErrorAction {
    type: "HANDLE_HTTP_ERROR";
    error: ErrorType | null;
}

export const handleHTTPError = (error: ErrorType | null): HandleHTTPErrorAction => ({
    type: "HANDLE_HTTP_ERROR",
    error,
});
