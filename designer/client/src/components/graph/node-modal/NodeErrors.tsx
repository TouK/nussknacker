import { css } from "@emotion/css";
import DangerousIcon from "@mui/icons-material/Dangerous";
import { FormHelperText } from "@mui/material";
import React from "react";

import type { NodeValidationError } from "../../../types";
import { InfoTooltip } from "./editors/InfoTooltip";

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
        marginRight: "4px",
        width: 24,
        height: 24,
    });

    return (
        <div className={className} style={{ alignItems: "center" }}>
            <InfoTooltip variant={"hover"} text={errorMessage} className={nodeTip}>
                <DangerousIcon sx={(theme) => ({ color: theme.palette.error.main, alignSelf: "center", width: 24, height: 24 })} />
            </InfoTooltip>
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
