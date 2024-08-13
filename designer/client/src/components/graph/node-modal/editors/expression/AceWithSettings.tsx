/* eslint-disable i18next/no-literal-string */
import { throttle } from "lodash";
import React, { ForwardedRef, forwardRef, useEffect, useMemo, useRef } from "react";
import ReactAce from "react-ace/lib/ace";
import { useMergeRefs } from "rooks";
import { useUserSettings } from "../../../../../common/userSettings";
import { UserSettings } from "../../../../../reducers/userSettings";
import AceWrapper, { AceKeyCommand, AceWrapperProps } from "./AceWrapper";

export default forwardRef(function AceWithSettings(
    props: Omit<AceWrapperProps, "noWrap" | "showLines">,
    ref: ForwardedRef<ReactAce>,
): JSX.Element {
    const [userSettings, toggleSettings] = useUserSettings();

    const [showLinesName, noWrapName] = useMemo<(keyof UserSettings)[]>(
        () => [`${props.inputProps.language}.showLines`, `${props.inputProps.language}.noWrap`],
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

        // before setting cursor position ensure all position calculations are actual
        const prepare = () => editor?.renderer.updateFull(true);

        const scrollToView = throttle(
            () => {
                document.activeElement.scrollIntoView({ block: "nearest", inline: "nearest" });
            },
            150,
            { leading: false },
        );

        editor?.addEventListener("mousedown", prepare);
        editor?.addEventListener("mouseup", scrollToView);
        selection?.on("changeCursor", scrollToView);
        return () => {
            editor?.removeEventListener("mousedown", prepare);
            editor?.removeEventListener("mouseup", scrollToView);
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
