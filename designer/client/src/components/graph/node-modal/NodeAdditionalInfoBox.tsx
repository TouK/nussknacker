import React, { useEffect, useState } from "react";
import HttpService from "../../../http/HttpService";
import ReactMarkdown from "react-markdown/with-html";
import { useDebounce } from "use-debounce";
import { NodeType } from "../../../types";
import { useSelector } from "react-redux";
import { getProcessName } from "./NodeDetailsContent/selectors";
import NodeUtils from "../NodeUtils";
import { styled } from "@mui/material";

interface Props {
    node: NodeType;
}

//Types should match implementations of AdditionalInfo on Backend!
export type AdditionalInfo = MarkdownAdditionalInfo;

interface MarkdownAdditionalInfo {
    type: "MarkdownAdditionalInfo";
    content: string;
}

const ReactMarkdownStyled = styled(ReactMarkdown)`
    margin-top: 20px;
    margin-bottom: 10px;
    font-size: 14px;
    table {
        background-color: #333;
        margin-top: 5px;
        margin-bottom: 5px;
        width: 95%;
    }
    th,
    td {
        padding: 10px 10px;
        border: 1px solid;
        border-color: #666;
        font-size: 12px;
    }
    a {
        color: #359af1 !important;
    }
`;

export default function NodeAdditionalInfoBox(props: Props): JSX.Element {
    const { node } = props;
    const processName = useSelector(getProcessName);

    const [additionalInfo, setAdditionalInfo] = useState<AdditionalInfo>(null);

    //We don't use redux here since this additionalInfo is local to this component. We use debounce, as
    //we don't wat to query BE on each key pressed (we send node parameters to get additional data)
    const [debouncedNode] = useDebounce(node, 1000);
    useEffect(() => {
        let ignore = false;
        if (processName) {
            const nodeType = NodeUtils.nodeType(debouncedNode) === "Properties";
            const promise = nodeType
                ? HttpService.getPropertiesAdditionalInfo(processName, debouncedNode)
                : HttpService.getNodeAdditionalInfo(processName, debouncedNode);
            promise.then(({ data }) => ignore || setAdditionalInfo(data));
        }
        return () => {
            ignore = true;
        };
    }, [processName, debouncedNode]);

    if (!additionalInfo?.type) {
        return null;
    }

    switch (additionalInfo.type) {
        case "MarkdownAdditionalInfo": {
            // eslint-disable-next-line i18next/no-literal-string
            const linkTarget = "_blank";
            return <ReactMarkdownStyled linkTarget={linkTarget}>{additionalInfo.content}</ReactMarkdownStyled>;
        }
        default:
            // eslint-disable-next-line i18next/no-literal-string
            console.warn("Unknown type:", additionalInfo.type);
            return null;
    }
}
