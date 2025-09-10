import { cx } from "@emotion/css";
import { styled } from "@mui/material";
import type { InputHTMLAttributes } from "react";
import React from "react";

import type { UnknownFunction } from "../../../../../types/common";
import { nodeInputWithError } from "../../NodeDetailsContent/NodeTableStyled";

const StyledInput = styled("input")(({ theme }) => ({
    width: "4em !important",
    outline: "none !important",
    backgroundColor: theme.palette.background.paper,
    textAlign: "center",
    padding: 0,
    border: 0,
    borderBottom: `1px solid ${theme.palette.common.white}`,
    "&:focus, &:focus-within": {
        borderBottom: `1px solid ${theme.palette.primary.main}`,
    },
    [`&.${nodeInputWithError}`]: {
        borderBottom: `1px solid ${theme.palette.error.light} !important`,
        borderOffset: "initial !important",
        borderRadius: 2,
    },
    "&.read-only": {
        borderBottom: 0,
    },
}));

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, "onChange" | "value"> & {
    onChange: UnknownFunction;
    value: number;
    fieldName?: string;
    readOnly?: boolean;
    showValidation?: boolean;
    isValid?: boolean;
    isMarked?: boolean;
};

export const NumericInput = ({ readOnly, value, onChange, showValidation, fieldName, isMarked, isValid, ...props }: Props) => {
    return (
        <StyledInput
            readOnly={readOnly}
            value={value ?? ""}
            onChange={(event) => onChange(fieldName, event.target.value)}
            className={cx([showValidation && !isValid && nodeInputWithError, isMarked && "marked", readOnly && "read-only"])}
            {...props}
        />
    );
};
