import { styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React, { forwardRef, useCallback } from "react";

import { useFieldsControl } from "../node-row-fields-provider/FieldsControl";
import { NodeRow } from "../node/NodeRow";
import { NodeValue } from "../node/NodeValue";
import { nodeValue } from "../NodeDetailsContent/NodeTableStyled";
import { RemoveButton } from "./buttons/RemoveButton";

interface FieldsRow {
    index: number;
    uuid: string;
    className?: string;
}

const Row = forwardRef<HTMLDivElement, PropsWithChildren<FieldsRow>>(function Row({ index, uuid, className, children }, forwardedRef) {
    const { remove } = useFieldsControl();
    const onClick = useCallback(() => remove?.(uuid), [uuid, remove]);
    return (
        <NodeRow className={className} data-testid={`fieldsRow:${index}`} ref={forwardedRef}>
            {children}
            {remove ? (
                <NodeValue className="fieldRemove">
                    <RemoveButton onClick={onClick} />
                </NodeValue>
            ) : null}
        </NodeRow>
    );
});

export const FieldsRow = styled(Row)({
    marginTop: 0,
    flexWrap: "nowrap",
    columnGap: 5,
    rowGap: 5,

    ".fieldName": {
        width: "28%",
    },

    [`.${nodeValue}`]: {
        "&.fieldName": {
            flexBasis: "30%",
            maxWidth: "20em",
        },
        "&.fieldRemove": {
            flex: 0,
        },
    },
});
