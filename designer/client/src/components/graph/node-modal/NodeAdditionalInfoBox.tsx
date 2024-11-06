import React, { useCallback, useEffect, useState } from "react";
import { useDebounce } from "use-debounce";
import { UINodeType } from "../../../types";
import { useSelector } from "react-redux";
import { getProcessName } from "./NodeDetailsContent/selectors";
import { MarkdownStyled } from "./MarkdownStyled";

interface Props {
    node: UINodeType;
    handleGetAdditionalInfo: (processName: string, node: UINodeType, controller: AbortController) => Promise<AdditionalInfo | null>;
}

//Types should match implementations of AdditionalInfo on Backend!
export type AdditionalInfo = MarkdownAdditionalInfo;

interface MarkdownAdditionalInfo {
    type: "MarkdownAdditionalInfo";
    content: string;
}

export default function NodeAdditionalInfoBox(props: Props): JSX.Element {
    const { node, handleGetAdditionalInfo } = props;
    const processName = useSelector(getProcessName);

    const [additionalInfo, setAdditionalInfo] = useState<AdditionalInfo>(null);

    //We don't use redux here since this additionalInfo is local to this component. We use debounce, as
    //we don't wat to query BE on each key pressed (we send node parameters to get additional data)
    const [debouncedNode] = useDebounce(node, 1000);

    const getAdditionalInfo = useCallback(
        (processName: string, debouncedNode: UINodeType) => {
            const controller = new AbortController();
            handleGetAdditionalInfo(processName, debouncedNode, controller).then((data) => {
                // signal should cancel request, but for some reason it doesn't in dev
                if (!controller.signal.aborted && data) {
                    setAdditionalInfo(data);
                }
            });
            return () => {
                controller.abort();
            };
        },
        [handleGetAdditionalInfo],
    );

    useEffect(() => {
        if (processName) {
            return getAdditionalInfo(processName, debouncedNode);
        }
    }, [debouncedNode, getAdditionalInfo, processName]);

    if (!additionalInfo?.type) {
        return null;
    }

    switch (additionalInfo.type) {
        case "MarkdownAdditionalInfo": {
            return <MarkdownStyled>{additionalInfo.content}</MarkdownStyled>;
        }
        default:
            // eslint-disable-next-line i18next/no-literal-string
            console.warn("Unknown type:", additionalInfo.type);
            return null;
    }
}
