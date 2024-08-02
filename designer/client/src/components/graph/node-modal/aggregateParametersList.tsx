import { ParametersList, ParametersListProps } from "./parametersList";
import { AggregateContextProvider } from "./aggregate/aggregateContext";
import { AggregateFieldOverrideWrapper } from "./customNodeParameters";
import React from "react";

export const AggregateParametersList = (props: ParametersListProps) => {
    const { errors, node, setProperty } = props;
    return (
        <AggregateContextProvider node={node} errors={errors} setProperty={setProperty}>
            <ParametersList {...props} FieldWrapper={AggregateFieldOverrideWrapper} />;
        </AggregateContextProvider>
    );
};
