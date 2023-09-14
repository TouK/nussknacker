import React from "react";
import DangerousIcon from "@mui/icons-material/Dangerous";
import NodeTip from "./NodeTip";
import { css } from "@emotion/css";
import { NodeValidationError } from "../../../types";
import { variables } from "../../../stylesheets/variables";

//TODO: remove style overrides, cleanup
export default function NodeErrors(props: { errors: NodeValidationError[]; message: string }): JSX.Element {
    const { errors = [], message: errorMessage } = props;

    if (!errors.length) {
        return null;
    }

    const className = css({
        display: "flex",
        margin: 0,
        "&&& .node-tip": {
            margin: ".5em 1em .5em 0",
        },
        "&&& .node-error": {
            padding: 0,
            margin: "0 0 .5em 0",
        },
    });

    return (
        <div className={className}>
            <NodeTip title={errorMessage} icon={<DangerousIcon sx={{ color: variables.alert.error, alignSelf: "center" }} />} />
            <div>
                {errors.map(({ description, fieldName, message }, index) => (
                    <div className="node-error" key={index} title={description}>
                        {message + (fieldName ? ` (field: ${fieldName})` : "")}
                    </div>
                ))}
            </div>
        </div>
    );
}
