import React from "react";
import { v4 as uuid4 } from "uuid";
import { isEmpty } from "lodash";
import { NodeValidationError } from "../../../types";
import { Props } from "./Errors";
import { NodeErrorsTips } from "./NodeErrorsTips";

export const ErrorTips = ({ errors, showDetails, scenario }: Props) => {
    const globalErrors = errors.globalErrors;
    const nodeErrors = errors.invalidNodes;
    const propertiesErrors = errors.processPropertiesErrors;

    const globalErrorsTips = (globalErrors: NodeValidationError[]) => <div>{globalErrors.map((error) => globalError(error, null))}</div>;

    const globalError = (error: NodeValidationError, suffix) => (
        <span key={uuid4()} title={error.description}>
            {(suffix ? `${suffix}: ` : "") + error.message + (error.fieldName ? `(${error.fieldName})` : "")}
        </span>
    );

    return isEmpty(nodeErrors) && isEmpty(propertiesErrors) && isEmpty(globalErrors) ? null : (
        <div>
            {globalErrorsTips(globalErrors)}
            <NodeErrorsTips
                propertiesErrors={errors.processPropertiesErrors}
                nodeErrors={errors.invalidNodes}
                showDetails={showDetails}
                scenario={scenario}
            />
        </div>
    );
};
