import React from "react";
import { styled } from "@mui/material";
import { Subtype } from "./Subtype";
import { Docs } from "./Docs";

const SubtypeHeaderDocs = styled(Docs)`
    background: #3a3a3a !important;
    height: 30px;
    &:hover {
        background-color: #585858 !important;
    }
`;

const SubtypeHeaderDocsLink = {
    color: "#ffffff !important",
    background: "#3a3a3a",
    padding: "0 10px",
    height: "30px",
    alignItems: "center",
    textDecoration: "none",
    display: "flex",
};

enum HeaderType {
    SUBTYPE_DOCS,
    SUBTYPE,
    DOCS,
    DEFAULT,
}

const getModalHeaderType = (docsUrl?: string, nodeClass?: string) => {
    if (docsUrl && nodeClass) {
        return HeaderType.SUBTYPE_DOCS;
    } else if (nodeClass) {
        return HeaderType.SUBTYPE;
    } else if (docsUrl) {
        return HeaderType.DOCS;
    } else {
        return HeaderType.DEFAULT;
    }
};

export const NodeClassDocs = ({ nodeClass, docsUrl }: { nodeClass?: string; docsUrl?: string }) => {
    switch (getModalHeaderType(docsUrl, nodeClass)) {
        case HeaderType.SUBTYPE_DOCS:
            return (
                <SubtypeHeaderDocs docsUrl={docsUrl} style={SubtypeHeaderDocsLink}>
                    {nodeClass}
                </SubtypeHeaderDocs>
            );
        case HeaderType.SUBTYPE:
            return <Subtype>{nodeClass}</Subtype>;
        case HeaderType.DOCS:
            return (
                <SubtypeHeaderDocs docsUrl={docsUrl} style={SubtypeHeaderDocsLink}>
                    {nodeClass}
                </SubtypeHeaderDocs>
            );
        default:
            return null;
    }
};
