import type { ComponentProps } from "react";
import React from "react";

import { isAggregate, isConditionBuilder, isDataMapper } from "../../../common/componentUtils";
import { useUserSettings } from "../../../common/useUserSettings";
import { AggregateParametersList } from "./aggregateParametersList";
import { ConditionBuilderParametersList } from "./conditionBuilderParametersList";
import { NamedParamsParametersList } from "./namedParamsParametersList";
import { ParametersListAdvanced } from "./parametersListAdvanced";

type Props = Omit<ComponentProps<typeof ParametersListAdvanced>, "FieldWrapper">;

export const ParametersListWithOverrides = (props: Props) => {
    const { node, parameterDefinitions } = props;
    const [showBuilder] = useUserSettings("node.showFieldExpressionBuilder");

    if (isAggregate(node)) {
        return <AggregateParametersList {...props} />;
    }

    if (showBuilder && isConditionBuilder(node)) {
        return <ConditionBuilderParametersList {...props} />;
    }

    if (showBuilder && isDataMapper(node, parameterDefinitions)) {
        return <NamedParamsParametersList {...props} />;
    }

    return <ParametersListAdvanced {...props} />;
};
