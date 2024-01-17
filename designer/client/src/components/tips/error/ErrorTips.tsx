import React from "react";
import { isEmpty } from "lodash";
import { Props } from "./Errors";
import { NodeErrorsTips } from "./NodeErrorsTips";

export const ErrorTips = ({ errors, showDetails, scenario }: Props) => {
    const globalErrors = errors.globalErrors;
    const nodeErrors = errors.invalidNodes;
    const propertiesErrors = errors.processPropertiesErrors;

    return isEmpty(nodeErrors) && isEmpty(propertiesErrors) && isEmpty(globalErrors) ? null : (
        <NodeErrorsTips
            propertiesErrors={errors.processPropertiesErrors}
            nodeErrors={errors.invalidNodes}
            globalErrors={errors.globalErrors}
            showDetails={showDetails}
            scenario={scenario}
        />
    );
};
