import type { PropsWithChildren } from "react";
import React from "react";
import { useTranslation } from "react-i18next";

import type { Parameter} from "../../../types";
import { ParameterCategory } from "../../../types";
import { Expandable } from "../../common/Expandable";
import type { ParameterExpressionFieldProps } from "./ParameterExpressionField";
import type { ParameterWithIndex } from "./parametersList";
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
    const paramsCategoryByName = parameterDefinitions.reduce((acc, paramDef) => {
        acc[paramDef.name] = paramDef.category ?? ParameterCategory.Standard;
        return acc;
    }, {} as Record<string, ParameterCategory>);
    const { standard, advanced } = parameters.reduce(
        (acc, param, index) => {
            const category = paramsCategoryByName[param.name] ?? ParameterCategory.Standard;
            acc[category.toLowerCase()].push({ index: index, param: param });
            return acc;
        },
        { standard: [], advanced: [] } as { standard: ParameterWithIndex[]; advanced: ParameterWithIndex[] },
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
