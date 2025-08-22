import { min } from "lodash";
import type { PropsWithChildren, ReactElement } from "react";
import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { ActionCreators as UndoActionCreators } from "redux-undo";
import { useDebounceFn } from "rooks";

import {
    copySelection,
    cutSelection,
    deleteNodes,
    deleteSelection,
    nodesWithEdgesAdded,
    pasteSelection,
    resetSelection,
    selectAll,
} from "../../actions/nk";
import { error, success } from "../../actions/notificationActions";
import type { Action } from "../../actions/reduxTypes";
import { readText, writeText } from "../../common/ClipboardUtils";
import { tryParseOrNull } from "../../common/JsonUtils";
import { isInputEvent } from "../../containers/BindKeyboardShortcuts";
import { useInterval } from "../../containers/Interval";
import { useDocumentListeners } from "../../containers/useDocumentListeners";
import { getGraphLocked, getHistoryCounts } from "../../reducers/selectors/getHistory";
import { getProcessDefinitionData } from "../../reducers/selectors/getProcessDefinitionData";
import { canModifySelectedNodes, getSelection, getSelectionState } from "../../reducers/selectors/graph";
import { getCapabilities } from "../../reducers/selectors/other";
import { useAppDispatch, useAppSelector } from "../../store/storeHelpers";
import NodeUtils from "./NodeUtils";

const hasTextSelection = () => !!window.getSelection().toString();

type UserAction = ((e: Event) => unknown) | null;

interface UserActions {
    copy: UserAction;
    paste: UserAction;
    cut: UserAction;
    delete: UserAction;
    undo: UserAction;
    redo: UserAction;
    selectAll: UserAction;
    deselectAll: UserAction;
}

function useClipboardParse() {
    const processDefinitionData = useAppSelector(getProcessDefinitionData);
    return useCallback(
        (text) => {
            const selection = tryParseOrNull(text);
            // TODO: check what happens with wrong nodes.
            const isValid = selection?.edges && selection?.nodes?.every((node) => NodeUtils.isAvailable(node, processDefinitionData));
            return isValid ? selection : null;
        },
        [processDefinitionData],
    );
}

function useClipboardPermission(): boolean | string {
    const clipboardPermission = useRef<PermissionStatus>();
    const [state, setState] = useState<"denied" | "granted" | "prompt">();
    const [text, setText] = useState("");
    const [content, setContent] = useState("");

    const parse = useClipboardParse();

    const checkClipboard = useCallback(async () => {
        try {
            setText(await navigator.clipboard.readText());
        } catch {} // eslint-disable-line no-empty
    }, []);

    // if possible monitor clipboard for new content
    useInterval(checkClipboard, {
        refreshTime: 500,
        disabled: state !== "granted",
    });

    useEffect(() => {
        // parse clipboard content on change only
        setContent(parse(text));
    }, [parse, text]);

    useEffect(() => {
        navigator.permissions
            ?.query({ name: "clipboard-read" as PermissionName })
            .then((permission) => {
                clipboardPermission.current = permission;
                setState(permission.state);
                permission.onchange = () => {
                    setState(permission.state);
                };
            })
            .catch(() => {
                /*do nothing*/
            });
        return () => {
            if (clipboardPermission.current) {
                clipboardPermission.current.onchange = undefined;
            }
        };
    }, []);

    return useMemo(() => {
        if (!clipboardPermission.current) {
            // "The clipboard-read and clipboard-write permissions are not supported (and not planned to be supported) by Firefox or Safari."
            // https://developer.mozilla.org/en-US/docs/Web/API/Clipboard_API
            return true;
        }
        return state === "prompt" || content;
    }, [content, state]);
}

const SelectionContext = createContext<UserActions>(null);

export const useSelectionActions = (): UserActions => {
    const selectionActions = useContext(SelectionContext);
    if (!selectionActions) {
        throw new Error("used useSelectionActions outside provider");
    }
    return selectionActions;
};

function useUndoRedoActions(disabled?: boolean) {
    const dispatch = useAppDispatch();
    const [past, future] = useAppSelector(getHistoryCounts);
    return useMemo(() => {
        return {
            undo: past <= 0 || disabled ? null : () => dispatch(UndoActionCreators.undo() as Action),
            redo: future <= 0 || disabled ? null : () => dispatch(UndoActionCreators.redo() as Action),
        };
    }, [past, disabled, future, dispatch]);
}

export default function SelectionContextProvider(
    props: PropsWithChildren<{
        pastePosition: () => { x: number; y: number };
    }>,
): ReactElement {
    const dispatch = useAppDispatch();
    const { t } = useTranslation();

    const selectionState = useAppSelector(getSelectionState);
    const capabilities = useAppSelector(getCapabilities);
    const selection = useAppSelector(getSelection);
    const canModifySelected = useAppSelector(canModifySelectedNodes);

    const [hasSelection, setHasSelection] = useState(hasTextSelection);

    const copy = useCallback(
        async (silent = false) => {
            if (silent && hasTextSelection()) {
                return;
            }

            if (canModifySelected) {
                await writeText(JSON.stringify(selection));
                return selection.nodes;
            } else {
                dispatch(error(t("userActions.copy.failed", "Can not copy selected content. It should contain only plain nodes")));
            }
        },
        [canModifySelected, dispatch, selection, t],
    );
    const cut = useCallback(
        async (isInternalEvent = false) => {
            const copied = await copy(true);
            if (copied) {
                const nodeIds = copied.map((node) => node.id);
                dispatch(deleteNodes(nodeIds));
                if (!isInternalEvent) {
                    dispatch(
                        success(
                            t("userActions.cut.success", {
                                defaultValue: "Cut node",
                                defaultValue_plural: "Cut {{count}} nodes",
                                count: copied.length,
                            }),
                        ),
                    );
                }
            }
        },
        [copy, dispatch, t],
    );

    const parse = useClipboardParse();

    function calculatePastedNodePosition(node, pasteX, minNodeX, pasteY, minNodeY) {
        const currentNodePosition = node.additionalFields.layoutData;
        const pasteNodePosition = { x: currentNodePosition.x + pasteX, y: currentNodePosition.y + pasteY };
        return {
            x: pasteNodePosition.x - minNodeX,
            y: pasteNodePosition.y - minNodeY,
        };
    }

    const [parseInsertNodes] = useDebounceFn((clipboardText) => {
        const selection = parse(clipboardText);
        if (selection) {
            const { x, y } = props.pastePosition();
            const minNodeX: number = min(selection.nodes.map((node) => node.additionalFields.layoutData.x));
            const minNodeY: number = min(selection.nodes.map((node) => node.additionalFields.layoutData.y));
            const nodesWithPositions = selection.nodes.map((node) => ({
                node,
                position: calculatePastedNodePosition(node, x, minNodeX, y, minNodeY),
            }));
            dispatch(nodesWithEdgesAdded(nodesWithPositions, selection.edges));
        } else {
            dispatch(error(t("userActions.paste.failed", "Cannot paste content from clipboard")));
        }
    }, 250);

    const paste = useCallback(
        async (event?: Event) => {
            if (isInputEvent(event)) {
                return;
            }
            try {
                const clipboardText = await readText(event);
                parseInsertNodes(clipboardText);
            } catch {
                dispatch(error(t("userActions.paste.notAvailable", "Paste button is not available. Try Ctrl+V")));
            }
        },
        [dispatch, parseInsertNodes, t],
    );

    const canAccessClipboard = useClipboardPermission();

    const graphLocked = useAppSelector(getGraphLocked);
    const undoRedoActions = useUndoRedoActions(graphLocked);
    const userActions: UserActions = useMemo(
        () => ({
            ...undoRedoActions,
            copy: canModifySelected && !hasSelection && (() => dispatch(copySelection(copy))),
            paste: !!canAccessClipboard && !graphLocked && capabilities.editFrontend && ((e) => dispatch(pasteSelection(() => paste(e)))),
            cut: !graphLocked && canModifySelected && capabilities.editFrontend && (() => dispatch(cutSelection(cut))),
            delete: !graphLocked && canModifySelected && capabilities.editFrontend && (() => dispatch(deleteSelection(selectionState))),
            selectAll: !graphLocked && (() => dispatch(selectAll())),
            deselectAll: !graphLocked && (() => dispatch(resetSelection())),
        }),
        [
            undoRedoActions,
            canModifySelected,
            hasSelection,
            graphLocked,
            canAccessClipboard,
            capabilities.editFrontend,
            dispatch,
            copy,
            paste,
            cut,
            selectionState,
        ],
    );

    useDocumentListeners(
        useMemo(
            () => ({
                selectionchange: () => setHasSelection(hasTextSelection),
            }),
            [],
        ),
    );

    return <SelectionContext.Provider value={userActions}>{props.children}</SelectionContext.Provider>;
}
