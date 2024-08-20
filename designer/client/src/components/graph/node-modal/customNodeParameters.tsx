import React, { useContext } from "react";
import { AggregateContext } from "./aggregate/aggregateContext";
import { AggregatorField } from "./aggregate/aggregatorField";
import { GroupByField } from "./aggregate/groupBy/groupByField";
import { FieldWrapperProps } from "./ParameterExpressionField";

export const AggregateFieldOverrideWrapper = function AggregateFieldOverrideWrapper({ children, ...props }: FieldWrapperProps) {
    const { aggregator, groupBy } = useContext(AggregateContext);
    const defaultElement = <>{children}</>;

    switch (props.parameter.name) {
        case "aggregator":
            return aggregator.values?.length ? <AggregatorField {...props} /> : defaultElement;
        case "aggregateBy":
            return aggregator.values?.length ? null : defaultElement;
        case "groupBy":
            return groupBy?.values ? <GroupByField {...props}>{children}</GroupByField> : defaultElement;
        default:
            return defaultElement;
    }
};
