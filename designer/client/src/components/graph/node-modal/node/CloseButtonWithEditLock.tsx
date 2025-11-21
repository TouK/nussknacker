import { DefaultComponents as Window } from "@touk/window-manager";
import type { HeaderButtonCloseProps } from "@touk/window-manager/cjs/components/window/header/HeaderButtonClose";
import React from "react";

import type { EditState } from "./useNodeState";

export const CloseButtonWithEditLock = ({
    closeDialog,
    editStateRef,
}: HeaderButtonCloseProps & { editStateRef: React.RefObject<EditState> }) => {
    return (
        <Window.HeaderButtonClose
            closeDialog={() => {
                function close(i = 0) {
                    if (editStateRef?.current === "idle" || i >= 10) return closeDialog();
                    setTimeout(() => close(++i), 200);
                }
                close();
            }}
        />
    );
};
