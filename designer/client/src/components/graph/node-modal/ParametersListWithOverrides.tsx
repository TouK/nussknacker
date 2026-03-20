import type { ComponentProps } from "react";
import React, { useMemo } from "react";

import { isAggregate, isConditionBuilder, isDataMapper } from "../../../common/componentUtils";
import { AggregateParametersList } from "./aggregateParametersList";
import { ConditionBuilderParametersList } from "./conditionBuilderParametersList";
import { NamedParamsParametersList } from "./namedParamsParametersList";
import { ParametersListAdvanced } from "./parametersListAdvanced";

type Props = Omit<ComponentProps<typeof ParametersListAdvanced>, "FieldWrapper">;

export const ParametersListWithOverrides = (props: Props) => {
    const { node, parameterDefinitions } = props;
    const ParametersComponent = useMemo(() => {
        if (isAggregate(node)) return AggregateParametersList;
        if (isConditionBuilder(node)) return ConditionBuilderParametersList;
        if (isDataMapper(node, parameterDefinitions)) return NamedParamsParametersList;
        return ParametersListAdvanced;
    }, [node, parameterDefinitions]);

    return <ParametersComponent {...props} />;
};
