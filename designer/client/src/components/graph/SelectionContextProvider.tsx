import React, {
    createContext,
    PropsWithChildren,
    ReactElement,
    useCallback,
    useContext,
    useEffect,
    useMemo,
    useRef,
    useState,
} from "react";
import { useTranslation } from "react-i18next";
import { useDispatch, useSelector } from "react-redux";
import { useDebouncedCallback } from "use-debounce";
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
import { ActionCreators as UndoActionCreators } from "redux-undo";
import * as ClipboardUtils from "../../common/ClipboardUtils";
import { tryParseOrNull } from "../../common/JsonUtils";
import { isInputEvent } from "../../containers/BindKeyboardShortcuts";
import { useDocumentListeners } from "../../containers/useDocumentListeners";
import { canModifySelectedNodes, getSelection, getSelectionState } from "../../reducers/selectors/graph";
import { getCapabilities } from "../../reducers/selectors/other";
import { getProcessDefinitionData } from "../../reducers/selectors/settings";
import NodeUtils from "./NodeUtils";
import { min } from "lodash";
import { useInterval } from "../../containers/Interval";

const hasTextSelection = () => !!window.getSelection().toString();

type UserAction = ((e: Event) => unknown) | null;

interface UserActions {
    copy: UserAction;
    paste: UserAction;
    canPaste: boolean;
    cut: UserAction;
    delete: UserAction;
    undo: UserAction;
    redo: UserAction;
    selectAll: UserAction;
    deselectAll: UserAction;
}

function useClipboardParse() {
    const processDefinitionData = useSelector(getProcessDefinitionData);
    return useCallback(
        (text) => {
            const selection = tryParseOrNull(text);
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

    return state === "prompt" || content;
}

const SelectionContext = createContext<UserActions>(null);

export const useSelectionActions = (): UserActions => {
    const selectionActions = useContext(SelectionContext);
    if (!selectionActions) {
        throw new Error("used useSelectionActions outside provider");
    }
    return selectionActions;
};

export default function SelectionContextProvider(
    props: PropsWithChildren<{
        pastePosition: () => { x: number; y: number };
    }>,
): ReactElement {
    const dispatch = useDispatch();
    const { t } = useTranslation();

    const selectionState = useSelector(getSelectionState);
    const capabilities = useSelector(getCapabilities);
    const selection = useSelector(getSelection);
    const canModifySelected = useSelector(canModifySelectedNodes);

    const [hasSelection, setHasSelection] = useState(hasTextSelection);

    const copy = useCallback(
        async (silent = false) => {
            if (silent && hasTextSelection()) {
                return;
            }

            if (canModifySelected) {
                await ClipboardUtils.writeText(JSON.stringify(selection));
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

    function calculatePastedNodePosition(node, pasteX, minNodeX, pasteY, minNodeY, random) {
        const currentNodePosition = node.additionalFields.layoutData;
        const pasteNodePosition = { x: currentNodePosition.x + pasteX, y: currentNodePosition.y + pasteY };
        const selectionLayoutNodePosition = { x: pasteNodePosition.x - minNodeX, y: pasteNodePosition.y - minNodeY };
        const randomizedNodePosition = {
            x: selectionLayoutNodePosition.x + random,
            y: selectionLayoutNodePosition.y + random,
        };
        return randomizedNodePosition;
    }

    const [parseInsertNodes] = useDebouncedCallback((clipboardText) => {
        const selection = parse(clipboardText);
        if (selection) {
            const { x, y } = props.pastePosition();
            const minNodeX: number = min(selection.nodes.map((node) => node.additionalFields.layoutData.x));
            const minNodeY: number = min(selection.nodes.map((node) => node.additionalFields.layoutData.y));
            const random = Math.floor(Math.random() * 20) + 1;
            const nodesWithPositions = selection.nodes.map((node) => ({
                node,
                position: calculatePastedNodePosition(node, x, minNodeX, y, minNodeY, random),
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
                const clipboardText = await ClipboardUtils.readText(event);
                parseInsertNodes(clipboardText);
            } catch {
                dispatch(error(t("userActions.paste.notAvailable", "Paste button is not available. Try Ctrl+V")));
            }
        },
        [dispatch, parseInsertNodes, t],
    );

    const canAccessClipboard = useClipboardPermission();
    const userActions: UserActions = useMemo(
        () => ({
            copy: canModifySelected && !hasSelection && (() => dispatch(copySelection(copy))),
            canPaste: !!canAccessClipboard,
            paste: capabilities.editFrontend && ((e) => dispatch(pasteSelection(() => paste(e)))),
            cut: canModifySelected && capabilities.editFrontend && (() => dispatch(cutSelection(cut))),
            delete: canModifySelected && capabilities.editFrontend && (() => dispatch(deleteSelection(selectionState))),
            undo: () => dispatch(UndoActionCreators.undo()),
            redo: () => dispatch(UndoActionCreators.redo()),
            selectAll: () => dispatch(selectAll()),
            deselectAll: () => dispatch(resetSelection()),
        }),
        [copy, cut, paste, selectionState, hasSelection, canAccessClipboard, canModifySelected, capabilities.editFrontend, dispatch],
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
