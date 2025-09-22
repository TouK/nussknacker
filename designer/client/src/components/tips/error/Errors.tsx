import type { SyntheticEvent } from "react";
import React from "react";
import { v4 as uuid4 } from "uuid";

import type { NodeOrPropertiesType } from "../../../types/node";
import type { ValidationErrors } from "../../../types/validation";
import type { Scenario } from "../../Process/types";
import { ErrorTips } from "./ErrorTips";
import { HeaderIcon } from "./HeaderIcon";

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
