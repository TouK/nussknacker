import type { ReactNode } from "react";
import React, { useCallback } from "react";

import type { DialogWithChildrenProps } from "../components/DialogWithChildren";
import { useWindows } from "../windowManager/useWindows";
import { WindowKind } from "../windowManager/WindowKind";
import { CustomTabPage } from "./CustomTabPage";

const TopicsTab = () => {
    const { open, close } = useWindows();

    const action = useCallback(
        async (title: string, children: ReactNode) => {
            const window = await open<DialogWithChildrenProps>({
                isResizable: true,
                isModal: true,
                shouldCloseOnEsc: true,
                kind: WindowKind.withChildren,
                width: 820,
                title,
                meta: { children },
            });
            return () => close(window.id);
        },
        [close, open],
    );

    return <CustomTabPage<{ open: typeof action }> id="topics" open={action} />;
};

export default TopicsTab;
