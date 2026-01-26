/* eslint-disable i18next/no-literal-string */
import { GlobalStyles } from "@mui/material";
import { throttle } from "lodash";
import type { ForwardedRef } from "react";
import React, { forwardRef, useEffect, useMemo, useRef } from "react";
import type ReactAce from "react-ace/lib/ace";
import { useMergeRefs } from "rooks";

import { userSettingsToggle } from "../../../../../actions/nk/userSettings";
import { getUserSettings } from "../../../../../reducers/selectors/userSettings";
import type { Setting } from "../../../../../reducers/userSettings";
import { useAppDispatch, useAppSelector } from "../../../../../store/storeHelpers";
import type { AceKeyCommand, AceWrapperProps } from "./AceWrapper";
import AceWrapper from "./AceWrapper";
import { useAceDndTarget } from "./useAceDndTarget";
import { useSetAceEditorAnnotations } from "./useAceEditorRangeMessages";

export type AceWithSettingsProps = Omit<AceWrapperProps, "noWrap" | "showLines">;

export default forwardRef(function AceWithSettings(props: AceWithSettingsProps, ref: ForwardedRef<ReactAce>): React.JSX.Element {
    const userSettings = useAppSelector(getUserSettings);
    const dispatch = useAppDispatch();

    const [showLinesName, noWrapName] = useMemo<Setting[]>(
        () => [`editor.${props.inputProps.language}.showLines`, `editor.${props.inputProps.language}.noWrap`],
        [props],
    );

    const commands = useMemo<AceKeyCommand[]>(
        () => [
            {
                name: "showLines",
                bindKey: { win: "F1", mac: "F1" },
                exec: () => {
                    dispatch(userSettingsToggle([showLinesName]));
                },
                readonly: true,
            },
            {
                name: "noWrap",
                bindKey: { win: "F2", mac: "F2" },
                exec: () => {
                    dispatch(userSettingsToggle([noWrapName]));
                },
                readonly: true,
            },
        ],
        [dispatch, showLinesName, noWrapName],
    );

    const editorRef = useRef<ReactAce>(null);
    useEffect(() => {
        const editor = editorRef.current?.editor;
        const selection = editor?.session.selection;

        const scrollToView = throttle(
            () => {
                if (!editor.isFocused()) return;
                // before setting cursor position ensure all position calculations are actual
                editor?.renderer.updateFull(true);
                const activeElement = editor.container.querySelector(".ace_cursor") || document.activeElement;
                activeElement.scrollIntoView({ block: "nearest", inline: "nearest" });
            },
            150,
            { leading: false },
        );

        selection?.on("changeCursor", scrollToView);
        return () => {
            selection?.off("changeCursor", scrollToView);
        };
    }, []);

    const mergedRefs = useMergeRefs(editorRef, ref);

    const { isOver } = useAceDndTarget(editorRef, props.inputProps.language);

    useSetAceEditorAnnotations(editorRef, props.annotations);

    return (
        <>
            {isOver ? (
                <GlobalStyles
                    styles={(theme) => ({
                        ".ace_ghost_text": {
                            color: theme.palette.info.main,
                            fontStyle: "normal",
                            opacity: 1,
                            borderBottom: "2px dashed",
                        },
                    })}
                />
            ) : null}

            <AceWrapper
                {...props}
                inputProps={{ ...props.inputProps, placeholder: isOver ? null : props.inputProps.placeholder }}
                ref={mergedRefs}
                commands={commands}
                showLineNumbers={userSettings[showLinesName]}
                wrapEnabled={!userSettings[noWrapName]}
            />
        </>
    );
});
