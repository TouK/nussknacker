import { useWindowManager, WindowId, WindowType } from "@touk/window-manager";
import { defaults } from "lodash";
import { useCallback, useEffect, useMemo } from "react";
import { useUserSettings } from "../common/userSettings";
import { ConfirmDialogData } from "../components/modals/GenericConfirmDialog";
import { InfoDialogData } from "../components/modals/GenericInfoDialog";
import { Scenario } from "../components/Process/types";
import { NodeType } from "../types";
import { WindowKind } from "./WindowKind";

export const NodeViewMode = {
    edit: false,
    readonly: true,
    descriptionView: "description",
    descriptionEdit: "descriptionEdit",
} as const;
export type NodeViewMode = (typeof NodeViewMode)[keyof typeof NodeViewMode];

function mapModeToKind(mode: NodeViewMode): WindowKind {
    switch (mode) {
        case NodeViewMode.readonly:
            return WindowKind.viewNode;
        case NodeViewMode.descriptionView:
            return WindowKind.viewDescription;
        case NodeViewMode.descriptionEdit:
            return WindowKind.editDescription;
    }
    return WindowKind.editNode;
}

const useRemoveFocusOnEscKey = (isWindowOpen: boolean) => {
    useEffect(() => {
        if (!isWindowOpen) {
            return;
        }

        const handleKeyDown = (event: KeyboardEvent) => {
            const activeElement = document.activeElement as HTMLElement;
            const tagName = activeElement.tagName.toLowerCase();
            const allowedTagNames = ["input", "textarea", "select"];

            if (event.key === "Escape" && allowedTagNames.includes(tagName)) {
                activeElement.blur(); // Removes focus from the current active element
            }
        };

        document.addEventListener("keydown", handleKeyDown);

        return () => {
            document.removeEventListener("keydown", handleKeyDown);
        };
    }, [isWindowOpen]);
};

export function useWindows(parent?: WindowId) {
    const { open: _open, closeAll, windows } = useWindowManager(parent);
    useRemoveFocusOnEscKey(windows.length > 0);
    const [settings] = useUserSettings();
    const forceDisableModals = useMemo(() => settings["debug.forceDisableModals"], [settings]);

    const open = useCallback(
        async <M = never>(windowData: Partial<WindowType<WindowKind, M>> = {}) => {
            const isModal = windowData.isModal === undefined ? !forceDisableModals : windowData.isModal && !forceDisableModals;
            return await _open({ isResizable: false, ...windowData, isModal });
        },
        [forceDisableModals, _open],
    );

    const openNodeWindow = useCallback(
        (node: NodeType, scenario: Scenario, viewMode: NodeViewMode = false, layoutData?: WindowType["layoutData"]) => {
            return open({
                id: node.id,
                title: node.id,
                isResizable: true,
                kind: mapModeToKind(viewMode),
                meta: { node, scenario },
                shouldCloseOnEsc: false,
                layoutData,
            });
        },
        [open],
    );

    const inform = useCallback(
        (data: InfoDialogData) => {
            return open({
                kind: WindowKind.inform,
                meta: data,
            });
        },
        [open],
    );

    const confirm = useCallback(
        (data: ConfirmDialogData) => {
            return open({
                title: data.text,
                kind: WindowKind.confirm,
                meta: defaults(data, { confirmText: "Yes", denyText: "No" }),
            });
        },
        [open],
    );

    return useMemo(
        () => ({
            open,
            confirm,
            inform,
            openNodeWindow,
            close: closeAll,
        }),
        [confirm, open, inform, openNodeWindow, closeAll],
    );
}
