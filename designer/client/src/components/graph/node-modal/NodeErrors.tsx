import React from "react";
import DangerousIcon from "@mui/icons-material/Dangerous";
import NodeTip from "./NodeTip";
import { css } from "@emotion/css";
import { NodeValidationError } from "../../../types";

//TODO: remove style overrides, cleanup
export default function NodeErrors(props: { errors: NodeValidationError[]; message: string }): JSX.Element {
    const { errors = [], message: errorMessage } = props;

    if (!errors.length) {
        return null;
    }

    const className = css({
        display: "flex",
    });

    const nodeTip = css({
        alignItems: "center",
        margin: 0,
        width: 24,
        height: 24,
    });

    return (
        <div className={className} style={{ alignItems: "center" }}>
            <NodeTip
                title={errorMessage}
                className={nodeTip}
                icon={<DangerousIcon sx={(theme) => ({ color: theme.custom.colors.error, alignSelf: "center", width: 24, height: 24 })} />}
            />
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
