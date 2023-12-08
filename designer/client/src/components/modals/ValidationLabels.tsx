import { styled } from "@mui/material";
import React from "react";
import { LimitedValidationLabel } from "../common/ValidationLabel";
import { isEmpty } from "lodash";
import { FieldError } from "../graph/node-modal/editors/Validators";

type Props = {
    fieldError: FieldError;
    validationLabelInfo?: string;
};

export type ValidationError = {
    errorType: string;
    fieldName: string;
    typ: string;
    message: string;
    description: string;
};

const LabelsContainer = styled("div")({
    display: "block",
    maxWidth: "fit-content",
});

export default function ValidationLabels(props: Props) {
    const { fieldError, validationLabelInfo } = props;

    // TODO: We're assuming that we have disjoint union of type info & validation errors, which is not always the case.
    // It's possible that expression is valid and it's type is known, but a different type is expected.
    return (
        <LabelsContainer>
            {isEmpty(fieldError) ? (
                <LimitedValidationLabel title={validationLabelInfo}>{validationLabelInfo}</LimitedValidationLabel>
            ) : (
                <LimitedValidationLabel key={fieldError.message} title={fieldError.message} type="ERROR">
                    {fieldError.message}
                </LimitedValidationLabel>
            )}
        </LabelsContainer>
    );
}
