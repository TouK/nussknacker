/* eslint-disable i18next/no-literal-string */
import { throttle } from "lodash";
import type { ForwardedRef } from "react";
import React, { forwardRef, useEffect, useMemo, useRef } from "react";
import type ReactAce from "react-ace/lib/ace";
import { useMergeRefs } from "rooks";

import { useUserSettings } from "../../../../../common/userSettings";
import type { UserSettings } from "../../../../../reducers/userSettings";
import type { AceKeyCommand, AceWrapperProps } from "./AceWrapper";
import AceWrapper from "./AceWrapper";

export default forwardRef(function AceWithSettings(
    props: Omit<AceWrapperProps, "noWrap" | "showLines">,
    ref: ForwardedRef<ReactAce>,
): JSX.Element {
    const [userSettings, toggleSettings] = useUserSettings();

    const [showLinesName, noWrapName] = useMemo<(keyof UserSettings)[]>(
        () => [`editor.${props.inputProps.language}.showLines`, `editor.${props.inputProps.language}.noWrap`],
        [props],
    );

    const commands = useMemo<AceKeyCommand[]>(
        () => [
            {
                name: "showLines",
                bindKey: { win: "F1", mac: "F1" },
                exec: () => toggleSettings([showLinesName]),
                readonly: true,
            },
            {
                name: "noWrap",
                bindKey: { win: "F2", mac: "F2" },
                exec: () => toggleSettings([noWrapName]),
                readonly: true,
            },
        ],
        [toggleSettings, showLinesName, noWrapName],
    );

    const editorRef = useRef<ReactAce>();
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

    return (
        <AceWrapper
            {...props}
            ref={mergedRefs}
            commands={commands}
            showLineNumbers={userSettings[showLinesName]}
            wrapEnabled={!userSettings[noWrapName]}
        />
    );
});
