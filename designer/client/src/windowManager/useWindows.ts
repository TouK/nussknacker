import type { WindowId, WindowType } from "@touk/window-manager";
import { useWindowManager } from "@touk/window-manager";
import { defaults } from "lodash";
import { useCallback, useEffect, useMemo } from "react";

import { useUserSettings } from "../common/userSettings";
import { StickyNoteType } from "../components/graph/utils/stickyNotesUtils";
import type { ConfirmDialogData } from "../components/modals/GenericConfirmDialog";
import type { InfoDialogData } from "../components/modals/GenericInfoDialog";
import type { Scenario } from "../components/Process/types";
import type { NodeType } from "../types";
import { WindowKind } from "./WindowKind";

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
    let windowManager: ReturnType<typeof useWindowManager>;

    try {
        windowManager = useWindowManager(parent);
    } catch (e) {
        throw "used outside WindowManager context";
    }

    const { open: _open, closeAll, windows } = windowManager;
    useRemoveFocusOnEscKey(windows.length > 0);
    const [settings] = useUserSettings();
    const forceDisableModals = useMemo(() => settings["debug.forceDisableModals"], [settings]);

    const margin = 30;
    const open = useCallback(
        async <M = never>(windowData: Partial<WindowType<WindowKind, M>> = {}) => {
            const isModal = windowData.isModal === undefined ? !forceDisableModals : windowData.isModal && !forceDisableModals;
            return await _open({
                isResizable: false,
                ...windowData,
                layoutData: {
                    top: margin * 2,
                    ...windowData.layoutData,
                },
                isModal,
            });
        },
        [forceDisableModals, _open],
    );

    const openNodeWindow = useCallback(
        (node: NodeType, scenario: Scenario, readonly?: boolean) => {
            if (node.type === StickyNoteType) return;
            return open({
                id: node.id,
                title: node.id,
                isResizable: true,
                kind: readonly ? WindowKind.viewNode : WindowKind.editNode,
                meta: { node, scenario },
                shouldCloseOnEsc: false,
                layoutData: settings["node.showInputsAndOutputs"]
                    ? {
                          width: window.innerWidth - 2 * margin,
                          height: window.innerHeight - 2 * margin,
                          top: margin,
                          left: margin,
                      }
                    : {
                          width: 820,
                          top: margin,
                          left: (window.innerWidth - 820) / 2,
                      },
            });
        },
        [open, settings],
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
                ...(data.width != null && { layoutData: { width: data.width } }),
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
