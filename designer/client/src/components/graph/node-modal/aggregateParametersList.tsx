import React from "react";
import { AggregateContextProvider } from "./aggregate/aggregateContext";
import { AggregateFieldOverrideWrapper } from "./customNodeParameters";
import { ParametersList, ParametersListProps } from "./parametersList";

export const AggregateParametersList = (props: ParametersListProps) => {
    const { errors, node, setProperty } = props;
    return (
        <AggregateContextProvider node={node} errors={errors} setProperty={setProperty}>
            <ParametersList {...props} FieldWrapper={AggregateFieldOverrideWrapper} />
        </AggregateContextProvider>
    );
};
