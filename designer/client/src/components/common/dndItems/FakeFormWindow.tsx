// provide parents css classes to dragged clone - temporary.
import { styled } from "@mui/material";
import type { PropsWithChildren } from "react";
import React from "react";

import { NodeTable } from "../../graph/node-modal/NodeDetailsContent/NodeTable";
import { nodeValue } from "../../graph/node-modal/NodeDetailsContent/NodeTableStyled";

const ModalContent = styled("div")({
    [`.${nodeValue}`]: {
        "&.fieldRemove": {
            flex: 0,
        },
    },
});

export function FakeFormWindow({ children }: PropsWithChildren<unknown>): React.JSX.Element {
    return (
        <ModalContent>
            <NodeTable className="fieldsControl">{children}</NodeTable>
        </ModalContent>
    );
}
