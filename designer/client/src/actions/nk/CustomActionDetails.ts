import HttpService, {CustomActionValidationRequest} from "../../http/HttpService";
import {ThunkAction} from "../reduxTypes";
import {NodeValidationError} from "../../types";
import {debounce} from "lodash";
import {ValidationData} from "./nodeDetails";

type CustomActionValidationUpdated = {
    type : "CUSTOM_ACTION_VALIDATION_UPDATED";
    validationData: ValidationData;
    actionName: string
};

export function customActionValidationUpdated(customActionName: string, validationData: ValidationData)
    : CustomActionValidationUpdated {
    return {
        type: "CUSTOM_ACTION_VALIDATION_UPDATED",
        validationData,
        actionName: customActionName
    }
}

const validate = debounce(
    async (
        processName: string,
        validationRequest: CustomActionValidationRequest,
        callback: (actionName: string, data?: ValidationData | void) => void
    ) => {
        const data = await HttpService.validateCustomAction(processName, validationRequest)
        const actionName = validationRequest.actionName

        callback(actionName, data)
    },
    500
    )

export function validateCustomAction(processName: string, validationRequest: CustomActionValidationRequest): ThunkAction {
    return (dispatch, getState) => {
        validate(processName, validationRequest, (actionName, data) => {
            if(data && getState){
                dispatch(customActionValidationUpdated(actionName, data));
            }
        })
    }
}
