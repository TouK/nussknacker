import React from "react";
import DangerousIcon from "@mui/icons-material/Dangerous";
import NodeTip from "./NodeTip";
import { css } from "@emotion/css";
import { NodeValidationError } from "../../../types";
import { FormHelperText } from "@mui/material";

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
                    <FormHelperText error variant={"largeMessage"} key={index} title={description}>
                        {message + (fieldName ? ` (field: ${fieldName})` : "")}
                    </FormHelperText>
                ))}
            </div>
        </div>
    );
}
