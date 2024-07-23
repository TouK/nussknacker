import { FieldWrapperProps } from "./ParameterExpressionField";
import React from "react";
import { AggregatorField } from "./aggregate/aggregatorField";

export const CustomNodeFieldOverrideWrapper = ({ children, ...props }: FieldWrapperProps) => {
    switch (props.node.nodeType) {
        case "aggregate-session":
        case "aggregate-sliding":
        case "aggregate-tumbling":
            switch (props.parameter.name) {
                case "aggregator":
                    return <AggregatorField {...props}>{children}</AggregatorField>;
                case "aggregateBy":
                    return null;
            }
    }
    return <>{children}</>;
};
