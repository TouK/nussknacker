import React from "react";
import { styled } from "@mui/material";
import { SubTypeHeader, Subtype } from "./Subtype";
import { Docs } from "./Docs";

const SubtypeHeaderDocs = styled(Docs)`
    ${SubTypeHeader}
    &:hover {
        background: #585858;
    }
`;

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
            return <SubtypeHeaderDocs docsUrl={docsUrl}>{nodeClass}</SubtypeHeaderDocs>;
        case HeaderType.SUBTYPE:
            return <Subtype>{nodeClass}</Subtype>;
        case HeaderType.DOCS:
            return <SubtypeHeaderDocs docsUrl={docsUrl}>{nodeClass}</SubtypeHeaderDocs>;
        default:
            return null;
    }
};
