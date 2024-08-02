import { FieldWrapperProps } from "./ParameterExpressionField";
import React, { useContext } from "react";
import { AggregatorField } from "./aggregate/aggregatorField";
import { AggregateContext } from "./aggregate/aggregateContext";

export const AggregateFieldOverrideWrapper = function AggregateFieldOverrideWrapper({ children, ...props }: FieldWrapperProps) {
    const { values } = useContext(AggregateContext);
    if (values?.length) {
        switch (props.parameter.name) {
            case "aggregator":
                return <AggregatorField {...props} />;
            case "aggregateBy":
                return null;
        }
    }
    return <>{children}</>;
};
