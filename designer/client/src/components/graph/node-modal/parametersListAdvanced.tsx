import { partition } from "lodash";
import type { PropsWithChildren } from "react";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import type { Parameter } from "../../../types";
import { ParameterCategory } from "../../../types";
import { Expandable } from "../../common/Expandable";
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

    const [standard, advanced] = useMemo(
        () =>
            partition(
                parameters.map((param, index) => ({ index, param })),
                (p) => getParamCategory(p.param.name) === ParameterCategory.Standard,
            ),
        [getParamCategory, parameters],
    );

    return (
        <>
            <ParametersList {...props} parameters={standard} getListFieldPath={getListFieldPath} />
            {children}
            {advanced.length > 0 && (
                <Expandable
                    componentId={"advanced-param-section"}
                    expandableTitle={t("component.advancedParameters.title", "Advanced parameters")}
                >
                    {<ParametersList {...props} parameters={advanced} getListFieldPath={getListFieldPath} />}
                </Expandable>
            )}
        </>
    );
};
