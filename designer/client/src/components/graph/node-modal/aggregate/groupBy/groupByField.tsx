import React, { useContext, useMemo } from "react";

import { useAppSelector } from "../../../../../store/storeHelpers";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { NodeRow } from "../../node/NodeRow";
import { NodeValue } from "../../node/NodeValue";
import { getFindAvailableVariables } from "../../NodeDetailsContent/selectors";
import type { FieldWrapperProps } from "../../ParameterExpressionField";
import { AggregateContext } from "../aggregateContext";
import { CollectionField } from "./collectionField";

export function GroupByField({ node, isEditMode, showValidation, parameter }: FieldWrapperProps) {
    const { groupBy } = useContext(AggregateContext);

    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables?.(node.id), [findAvailableVariables, node.id]);

    return (
        <NodeRow label={"groupBy"}>
            <NodeValue>
                <CollectionField
                    value={groupBy.values}
                    onChange={groupBy.onChange}
                    variableTypes={variableTypes}
                    disabled={isEditMode}
                    isValueValid={showValidation ? (value) => !groupBy.errorsByValue.has(value) : undefined}
                />
                <ValidationLabels fieldErrors={groupBy.fieldErrors} />
            </NodeValue>
        </NodeRow>
    );
}
