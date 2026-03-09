import type { WindowId, WindowType } from "@touk/window-manager";
import { useWindowManager } from "@touk/window-manager";
import { defaults } from "lodash";
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef } from "react";

import { useUserSettings } from "../common/useUserSettings";
import { StickyNoteType } from "../components/graph/utils/stickyNotesUtils";
import type { ConfirmDialogData } from "../components/modals/GenericConfirmDialog";
import type { InfoDialogData } from "../components/modals/GenericInfoDialog";
import type { Scenario } from "../components/Process/types";
import { getWindowsIdMapping } from "../reducers/selectors/getWindowsIdMapping";
import type { AppState } from "../store/storeHelpers";
import { useAppSelector } from "../store/storeHelpers";
import type { NodeType } from "../types/node";
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

function useAppSelectorRef<T>(selector: (state: AppState) => T) {
    const selected = useAppSelector(selector);
    const result = useRef(selected);
    useLayoutEffect(() => {
        result.current = selected;
    }, [selected]);
    return result;
}

const DEFAULT_WINDOW_MARGIN = 30;
export const DEFAULT_WINDOW_WIDTH = 820;

export function useWindows(parent?: WindowId) {
    let windowManager: ReturnType<typeof useWindowManager>;

    try {
        windowManager = useWindowManager(parent);
    } catch (e) {
        throw "used outside WindowManager context";
    }

    const { open: _open, close, closeAll, windows } = windowManager;
    useRemoveFocusOnEscKey(windows.length > 0);
    const [forceDisableModals, showInputsAndOutputs] = useUserSettings("debug.forceDisableModals", "node.showInputsAndOutputs");

    const open = useCallback(
        async <M = never>(windowData: Partial<WindowType<WindowKind, M>> = {}) => {
            const isModal = windowData.isModal === undefined ? !forceDisableModals : windowData.isModal && !forceDisableModals;
            return await _open({
                isResizable: false,
                ...windowData,
                layoutData: {
                    top: DEFAULT_WINDOW_MARGIN * 2,
                    ...windowData.layoutData,
                },
                isModal,
            });
        },
        [forceDisableModals, _open],
    );

    const nodeWindowIdMap = useAppSelectorRef(getWindowsIdMapping);

    const openNodeWindow = useCallback(
        (node: NodeType, scenario: Scenario, readonly?: boolean) => {
            if (node.type === StickyNoteType) return;
            if (nodeWindowIdMap.current[node.id]) return;
            return open({
                // , and / allowed in nodeId
                id: encodeURIComponent(node.id),
                title: node.name,
                isResizable: true,
                kind: readonly ? WindowKind.viewNode : WindowKind.editNode,
                meta: {
                    node,
                    scenario,
                },
                shouldCloseOnEsc: false,
                layoutData: showInputsAndOutputs
                    ? {
                          width: window.innerWidth - 2 * DEFAULT_WINDOW_MARGIN,
                          height: window.innerHeight - 2 * DEFAULT_WINDOW_MARGIN,
                          top: DEFAULT_WINDOW_MARGIN,
                          left: DEFAULT_WINDOW_MARGIN,
                      }
                    : {
                          width: DEFAULT_WINDOW_WIDTH,
                          top: DEFAULT_WINDOW_MARGIN,
                          left: (window.innerWidth - DEFAULT_WINDOW_WIDTH) / 2,
                      },
            });
        },
        [nodeWindowIdMap, open, showInputsAndOutputs],
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
                meta: defaults(data, {
                    confirmText: "Yes",
                    denyText: "No",
                }),
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
            closeAll,
            close,
        }),
        [open, confirm, inform, openNodeWindow, closeAll, close],
    );
}
