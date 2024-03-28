import React from "react";
import { Theme, useTheme } from "@mui/material";
import { Subtype } from "./Subtype";
import { Docs } from "./Docs";
import { blendLighten } from "../../../../containers/theme/nuTheme";

const SubtypeHeaderDocsLink = (theme: Theme) => ({
    backgroundColor: blendLighten(theme.palette.background.paper, 0.1),
    padding: "0 10px",
    height: "30px",
    alignItems: "center",
    textDecoration: "none",
    display: "flex",
});

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
    const theme = useTheme();

    switch (getModalHeaderType(docsUrl, nodeClass)) {
        case HeaderType.SUBTYPE_DOCS:
            return (
                <Docs docsUrl={docsUrl} style={SubtypeHeaderDocsLink(theme)}>
                    {nodeClass}
                </Docs>
            );
        case HeaderType.SUBTYPE:
            return <Subtype>{nodeClass}</Subtype>;
        case HeaderType.DOCS:
            return (
                <Docs docsUrl={docsUrl} style={SubtypeHeaderDocsLink(theme)}>
                    {nodeClass}
                </Docs>
            );
        default:
            return null;
    }
};
