import React, { useCallback, useEffect, useState } from "react";
import HttpService from "../../../http/HttpService";
import ReactMarkdown from "react-markdown/with-html";
import { useDebounce } from "use-debounce";
import { NodeType } from "../../../types";
import { useSelector } from "react-redux";
import { getProcessName } from "./NodeDetailsContent/selectors";
import NodeUtils from "../NodeUtils";
import { styled } from "@mui/material";

import { getBorderColor } from "../../../containers/theme/helpers";

interface Props {
    node: NodeType;
}

//Types should match implementations of AdditionalInfo on Backend!
export type AdditionalInfo = MarkdownAdditionalInfo;

interface MarkdownAdditionalInfo {
    type: "MarkdownAdditionalInfo";
    content: string;
}

const ReactMarkdownStyled = styled(ReactMarkdown)(({ theme }) => ({
    ...theme.typography.body2,
    marginTop: theme.spacing(1.5),
    marginBottom: theme.spacing(1.25),
    table: {
        backgroundColor: theme.palette.background.paper,
        marginTop: theme.spacing(0.5),
        marginBottom: theme.spacing(0.5),
        width: "95%",
    },
    "th, td": {
        padding: theme.spacing(1.25),
        border: `1px solid ${getBorderColor(theme)}`,
        fontSize: "12px",
    },
    a: {
        color: `${theme.palette.primary.main} !important`,
    },
}));

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
