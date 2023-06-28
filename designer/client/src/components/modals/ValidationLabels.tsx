import styled from "@emotion/styled";
import { isEmpty } from "lodash";
import React from "react";
import { LimitedValidationLabel } from "../common/ValidationLabel";
import { Validator, withoutDuplications } from "../graph/node-modal/editors/Validators";

type Props = {
    validators: Array<Validator>;
    values: Array<string>;
    validationLabelInfo?: string;
};

type ValidationError = {
    message: string;
    description: string;
};

const LabelsContainer = styled.div({
    display: "inline-grid",
    maxWidth: "fit-content",
});

export default function ValidationLabels(props: Props) {
    const { validators, values, validationLabelInfo } = props;

    const validationErrors: ValidationError[] = withoutDuplications(validators)
        .filter((v) => !v.isValid(...values))
        .map((validator) => ({
            message: validator.message && validator.message(),
            description: validator.description && validator.description(),
        }));

    const isValid: boolean = isEmpty(validationErrors);

    const renderErrorLabels = () =>
        validationErrors.map((validationError) => {
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
