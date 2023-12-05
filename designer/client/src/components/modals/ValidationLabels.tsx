import { styled } from "@mui/material";
import { isEmpty } from "lodash";
import React from "react";
import { LimitedValidationLabel } from "../common/ValidationLabel";
import { NodeValidationError } from "../../types";

type Props = {
    fieldErrors: NodeValidationError[];
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
    const { fieldErrors, validationLabelInfo } = props;

    const isValid: boolean = isEmpty(fieldErrors);

    const renderErrorLabels = () =>
        fieldErrors.map((validationError) => {
            // we don't pass description as tooltip message until we make changes on the backend
            return (
                <LimitedValidationLabel key={validationError.message} title={validationError.message} type="ERROR">
                    {validationError.message}
                </LimitedValidationLabel>
            );
        });

    // TODO: We're assuming that we have disjoint union of type info & validation errors, which is not always the case.
    // It's possible that expression is valid and it's type is known, but a different type is expected.
    return (
        <LabelsContainer>
            {isValid ? (
                <LimitedValidationLabel title={validationLabelInfo}>{validationLabelInfo}</LimitedValidationLabel>
            ) : (
                renderErrorLabels()
            )}
        </LabelsContainer>
    );
}
