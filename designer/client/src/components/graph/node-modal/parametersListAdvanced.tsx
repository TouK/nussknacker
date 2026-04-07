import { partition } from "lodash";
import type { PropsWithChildren } from "react";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { ParameterCategory } from "../../../types/definition";
import type { Parameter } from "../../../types/node";
import { Expandable } from "../../common/Expandable";
import { LabelWithErrorIndicator } from "../../common/LabelWithErrorIndicator";
import type { ParameterExpressionFieldProps } from "./ParameterExpressionField";
import { ParametersList } from "./parametersList";

type ParametersListItemProps = Omit<ParameterExpressionFieldProps, "listFieldPath" | "parameter">;

export type AdvancedParametersListProps = ParametersListItemProps & {
    parameters: Parameter[];
    getListFieldPath: (index: number) => string;
};

export const ParametersListAdvanced = ({
    children,
    parameters = [],
    getListFieldPath,
    ...props
}: PropsWithChildren<AdvancedParametersListProps>) => {
    const { t } = useTranslation();
    const { parameterDefinitions } = props;

    const getParamCategory = useCallback(
        (name: string) => {
            const paramDef = parameterDefinitions?.find((paramDef) => paramDef.name === name);
            return paramDef?.category || ParameterCategory.Standard;
        },
        [parameterDefinitions],
    );

    const { errors } = props;

    const [standard, advanced] = useMemo(
        () =>
            partition(
                parameters.map((param, index) => ({ index, param })),
                (p) => getParamCategory(p.param.name) === ParameterCategory.Standard,
            ),
        [getParamCategory, parameters],
    );

    const hasAdvancedErrors = useMemo(
        () => advanced.some(({ param }) => errors?.some((e) => e.fieldName === param.name)),
        [advanced, errors],
    );

    const advancedLabel = t("component.advancedParameters.title", "Advanced parameters");
    const advancedTitle = useMemo(
        () => (hasAdvancedErrors ? <LabelWithErrorIndicator label={advancedLabel} hasError /> : advancedLabel),
        [advancedLabel, hasAdvancedErrors],
    );

    return (
        <>
            <ParametersList {...props} parameters={standard} getListFieldPath={getListFieldPath} />
            {children}
            {advanced.length > 0 && (
                <Expandable componentId={"advanced-param-section"} expandableTitle={advancedTitle}>
                    <ParametersList {...props} parameters={advanced} getListFieldPath={getListFieldPath} />
                </Expandable>
            )}
        </>
    );
};
