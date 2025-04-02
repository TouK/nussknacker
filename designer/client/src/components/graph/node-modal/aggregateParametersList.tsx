import type { PropsWithChildren } from "react";
import React from "react";

import { AggregateContextProvider } from "./aggregate/aggregateContext";
import { AggregateFieldOverrideWrapper } from "./customNodeParameters";
import { AdvancedParametersListProps, ParametersListAdvanced } from "./parametersListAdvanced";

export const AggregateParametersList = ({ children, ...props }: PropsWithChildren<AdvancedParametersListProps>) => {
    const { errors, node, setProperty } = props;
    return (
        <AggregateContextProvider node={node} errors={errors} setProperty={setProperty}>
            <ParametersListAdvanced {...props} FieldWrapper={AggregateFieldOverrideWrapper}>
                {children}
            </ParametersListAdvanced>
        </AggregateContextProvider>
    );
};
