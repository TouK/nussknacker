import type { ComponentProps } from "react";
import React, { useMemo } from "react";

import { AggregateParametersList } from "./aggregateParametersList";
import { isAggregate } from "./isAggregate";
import { ParametersListAdvanced } from "./parametersListAdvanced";

type Props = Omit<ComponentProps<typeof ParametersListAdvanced>, "FieldWrapper">;

export const ParametersListWithOverrides = (props: Props) => {
    const ParametersComponent = useMemo(() => {
        if (isAggregate(props.node)) return AggregateParametersList;
        return ParametersListAdvanced;
    }, [props.node]);

    return <ParametersComponent {...props} />;
};
