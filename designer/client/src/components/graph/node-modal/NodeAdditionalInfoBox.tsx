import React, { useCallback, useEffect, useState } from "react";
import HttpService from "../../../http/HttpService";
import { useDebounce } from "use-debounce";
import { NodeType } from "../../../types";
import { useSelector } from "react-redux";
import { getProcessName } from "./NodeDetailsContent/selectors";
import NodeUtils from "../NodeUtils";
import { MarkdownStyled } from "./MarkdownStyled";

interface Props {
    node: NodeType;
}

//Types should match implementations of AdditionalInfo on Backend!
export type AdditionalInfo = MarkdownAdditionalInfo;

interface MarkdownAdditionalInfo {
    type: "MarkdownAdditionalInfo";
    content: string;
}

export default function NodeAdditionalInfoBox(props: Props): JSX.Element {
    const { node } = props;
    const processName = useSelector(getProcessName);

    const [additionalInfo, setAdditionalInfo] = useState<AdditionalInfo>(null);

    //We don't use redux here since this additionalInfo is local to this component. We use debounce, as
    //we don't wat to query BE on each key pressed (we send node parameters to get additional data)
    const [debouncedNode] = useDebounce(node, 1000);

    const getAdditionalInfo = useCallback((processName: string, debouncedNode: NodeType) => {
        const controller = new AbortController();
        const fetch = (node: NodeType) =>
            NodeUtils.nodeIsProperties(node)
                ? HttpService.getPropertiesAdditionalInfo(processName, node, controller)
                : HttpService.getNodeAdditionalInfo(processName, node, controller);

        fetch(debouncedNode).then((data) => {
            // signal should cancel request, but for some reason it doesn't in dev
            if (!controller.signal.aborted && data) {
                setAdditionalInfo(data);
            }
        });
        return () => {
            controller.abort();
        };
    }, []);

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
