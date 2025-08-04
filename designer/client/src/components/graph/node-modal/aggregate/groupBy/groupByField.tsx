import React, { useContext, useMemo } from "react";

import { useAppSelector } from "../../../../../store/storeHelpers";
import ValidationLabels from "../../../../modals/ValidationLabels";
import { NodeRow, NodeValue } from "../../node";
import { getFindAvailableVariables } from "../../NodeDetailsContent/selectors";
import type { FieldWrapperProps } from "../../ParameterExpressionField";
import { AggregateContext } from "../aggregateContext";
import { CollectionField } from "./collectionField";

export function GroupByField({ node, isEditMode }: FieldWrapperProps) {
    const { groupBy } = useContext(AggregateContext);

    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables?.(node.id), [findAvailableVariables, node.id]);

    return (
        <NodeRow label={"groupBy"}>
            <NodeValue>
                <CollectionField value={groupBy.values} onChange={groupBy.onChange} variableTypes={variableTypes} disabled={isEditMode} />
                <ValidationLabels fieldErrors={groupBy.fieldErrors} />
            </NodeValue>
        </NodeRow>
    );
}
