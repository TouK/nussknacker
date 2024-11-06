import React, { SyntheticEvent } from "react";
import { v4 as uuid4 } from "uuid";
import { HeaderIcon } from "./HeaderIcon";
import { NodeOrPropertiesType, ValidationErrors } from "../../../types";
import { ErrorTips } from "./ErrorTips";
import { Scenario } from "../../Process/types";

export interface Props {
    errors: ValidationErrors;
    showDetails: (event: SyntheticEvent, details: NodeOrPropertiesType) => void;
    scenario: Scenario;
}

function Errors({ errors = { globalErrors: [], invalidNodes: {}, processPropertiesErrors: [] }, showDetails, scenario }: Props) {
    return (
        <div key={uuid4()} style={{ display: "flex", alignItems: "center" }}>
            <HeaderIcon errors={errors} />
            <ErrorTips errors={errors} showDetails={showDetails} scenario={scenario} />
        </div>
    );
}

export default Errors;
