import React, { PropsWithChildren, useCallback } from "react";
import { NodeRow } from "./NodeRow";
import { useFieldsContext } from "./NodeRowFields";
import { NodeValue } from "./NodeValue";
import { RemoveButton } from "./RemoveButton";
import { cx } from "@emotion/css";

interface FieldsRow {
    index: number;
    className?: string;
    style?: React.CSSProperties;
}

export function FieldsRow({ index, className, children, style }: PropsWithChildren<FieldsRow>): JSX.Element {
    const { readOnly, remove } = useFieldsContext();
    const onClick = useCallback(() => remove?.(index), [index, remove]);
    return (
        <NodeRow className={cx("movable-row", className)} style={style} data-testid={`fieldsRow:${index}`}>
            {children}
            {!readOnly && remove && (
                <NodeValue className="fieldRemove">
                    <RemoveButton onClick={onClick} />
                </NodeValue>
            )}
        </NodeRow>
    );
}
