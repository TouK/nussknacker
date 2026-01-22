import type { PropsWithChildren } from "react";
import React from "react";

import { AggregateContextProvider } from "./aggregate/aggregateContext";
import { AggregateFieldOverrideWrapper } from "./customNodeParameters";
import type { AdvancedParametersListProps } from "./parametersListAdvanced";
import { ParametersListAdvanced } from "./parametersListAdvanced";

type AggregateParametersListProps = Omit<PropsWithChildren<AdvancedParametersListProps>, "FieldWrapper">;
export const AggregateParametersList = ({ children, ...props }: AggregateParametersListProps) => {
    const { errors, node, setProperty } = props;
    return (
        <AggregateContextProvider node={node} errors={errors} setProperty={setProperty}>
            <ParametersListAdvanced {...props} FieldWrapper={AggregateFieldOverrideWrapper}>
                {children}
            </ParametersListAdvanced>
        </AggregateContextProvider>
    );
};
