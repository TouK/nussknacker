import React, { PropsWithChildren, useCallback } from "react";
import { NodeRow } from "./NodeRow";
import { useFieldsContext } from "./NodeRowFields";
import { NodeValue } from "./NodeValue";
import { RemoveButton } from "./RemoveButton";
import { cx } from "@emotion/css";
import { styled } from "@mui/material";

const MovableRow = styled(NodeRow)`
    margin-top: 0;
    flex-wrap: nowrap;
    column-gap: 5px;
    row-gap: 5px;
`;

export function FieldsRow({ index, className, children }: PropsWithChildren<{ index: number; className?: string }>): JSX.Element {
    const { readOnly, remove } = useFieldsContext();
    const onClick = useCallback(() => remove?.(index), [index, remove]);
    return (
        <NodeRow className={cx("movable-row", className)} data-testid={`fieldsRow:${index}`}>
            {children}
            {!readOnly && remove && (
                <NodeValue className="fieldRemove">
                    <RemoveButton onClick={onClick} />
                </NodeValue>
            )}
        </NodeRow>
    );
}
