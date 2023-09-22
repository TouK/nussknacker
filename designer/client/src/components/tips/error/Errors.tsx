import React, { SyntheticEvent } from "react";
import { v4 as uuid4 } from "uuid";
import { HeaderIcon } from "./HeaderIcon";
import { NodeType, Process, ValidationErrors } from "../../../types";
import { ErrorTips } from "./ErrorTips";

export interface Props {
    errors: ValidationErrors;
    showDetails: (event: SyntheticEvent, details: NodeType) => void;
    currentProcess: Process;
}

function Errors({ errors = { globalErrors: [], invalidNodes: {}, processPropertiesErrors: [] }, showDetails, currentProcess }: Props) {
    return (
        <div key={uuid4()} style={{ display: "flex", alignItems: "center" }}>
            <HeaderIcon errors={errors} />
            <ErrorTips errors={errors} showDetails={showDetails} currentProcess={currentProcess} />
        </div>
    );
}

export default Errors;
