import styled from "@emotion/styled";
import { isEmpty } from "lodash";
import React, { useMemo } from "react";
import { LimitedValidationLabel } from "../common/ValidationLabel";
import { TemplateValues, Validator, withoutDuplications } from "../graph/node-modal/editors/Validators";
import { Trans, useTranslation } from "react-i18next";

type Props = {
    validators: Array<Validator>;
    values: Array<string>;
    validationLabelInfo?: string;
};

type ValidationError = {
    message: string;
    description: string;
    templateValues: TemplateValues;
    errorCode?: string;
};

const LabelsContainer = styled.div({
    display: "inline-grid",
    maxWidth: "fit-content",
});

export default function ValidationLabels(props: Props) {
    const { validators, values, validationLabelInfo } = props;
    const { t } = useTranslation();

    const validationErrors: ValidationError[] = withoutDuplications(validators)
        .filter((v) => !v.isValid(...values))
        .map((validator) => ({
            message: validator.message && validator.message(),
            description: validator.description && validator.description(),
            templateValues: validator.templateValues && validator.templateValues(),
            errorCode: validator.errorCode && validator.errorCode(),
        }));

    const isValid: boolean = isEmpty(validationErrors);

    const renderErrorLabels = () =>
        validationErrors.map((validationError) => {
            const { errorCode, templateValues, message } = validationError;

            const canRenderI18nTemplate = errorCode != null && templateValues != null;

            // we don't pass description as tooltip message until we make changes on the backend
            return (
                <LimitedValidationLabel key={message} type="ERROR" title={message}>
                    {canRenderI18nTemplate ? (
                        <I18nTemplateLabel errorCode={errorCode} templateValues={validationError.templateValues} />
                    ) : (
                        t(`nodeErrorCodes:${validationError.errorCode}.simpleMessage`, message)
                    )}
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

type TemplateLabelProps = {
    errorCode: string;
    templateValues: TemplateValues;
};

function I18nTemplateLabel(props: TemplateLabelProps) {
    const { errorCode, templateValues } = props;
    const { i18n } = useTranslation();

    const displayableTemplateValues = useMemo(() => {
        return {
            ...templateValues.stringValues,
            ...Object.fromEntries(
                Object.entries(templateValues.typingResultValues).map(([key, typingResult]) => {
                    return [key, typingResult.display];
                }),
            ),
        };
    }, [templateValues]);

    // we create separate spans only for TypingResult values
    const templateComponents = useMemo(() => {
        return Object.fromEntries(
            Object.entries(templateValues.typingResultValues).map(([key, typingResult]) => {
                const component = <span title={typingResult.display} />;
                return [key, component];
            }),
        );
    }, [templateValues]);

    const canRender =
        i18n.exists(`nodeErrorCodes:${errorCode}.messageTemplate`) &&
        validateTemplateInterpolation(
            i18n.t(`nodeErrorCodes:${errorCode}.messageTemplate`),
            Object.keys(displayableTemplateValues),
            Object.keys(templateComponents),
        );

    return canRender ? (
        <Trans
            i18nKey={`nodeErrorCodes:${errorCode}.messageTemplate`}
            values={displayableTemplateValues}
            components={templateComponents}
        ></Trans>
    ) : null;
}

function validateTemplateInterpolation(template: string, valueNames: string[], componentNames: string[]): boolean {
    const allValuesInterpolated = valueNames.every((name) => template.includes(`{{${name}}}`));
    const allComponentsInterpolated = componentNames.every((name) => template.includes(`<${name}>`));
    return allValuesInterpolated && allComponentsInterpolated;
}
