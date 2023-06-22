import React from "react";
import { isEmpty } from "lodash";
import { Validator, withoutDuplications } from "../graph/node-modal/editors/Validators";
import { css, cx } from "@emotion/css";

type Props = {
    validators: Array<Validator>;
    values: Array<string>;
    validationLabelInfo?: string;
};

type ValidationError = {
    message: string;
    description: string;
};

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
        validationErrors.map((validationError) => (
            // we don't pass description as tooltip message - we pass the entire non-line-limited string
            // until we make changes on the backend
            <LimitedValidationLabel
                key={validationError.message}
                message={validationError.message}
                tooltipMessage={validationError.message}
                type={"ERROR"}
            />
        ));

    // TODO: We're assuming that we have disjoint union of type info & validation errors, which is not always the case.
    // It's possible that expression is valid and it's type is known, but a different type is expected.
    return (
        <div className={`validation-labels`}>
            {isValid ? (
                <LimitedValidationLabel message={validationLabelInfo} tooltipMessage={validationLabelInfo} type={"INFO"} />
            ) : (
                renderErrorLabels()
            )}
        </div>
    );
}

type ValidationLabelProps = {
    message: string;
    tooltipMessage: string;
    type: ValidationLabelType;
};

type ValidationLabelType = "INFO" | "ERROR";

function LimitedValidationLabel(props: ValidationLabelProps): JSX.Element {
    const labelTypeStyle = () => {
        switch (props.type) {
            case "INFO":
                return "validation-label-info";
            case "ERROR":
                return "validation-label-error";
        }
    };
    const lineLimitStyle = css({
        display: "-webkit-box",
        WebkitLineClamp: 3,
        WebkitBoxOrient: "vertical",
        overflow: "hidden",
    });

    return (
        <span className={cx(labelTypeStyle(), lineLimitStyle)} title={props.tooltipMessage}>
            {props.message}
        </span>
    );
}
