import { Box, styled, Typography } from "@mui/material";
import { PropsOf } from "@emotion/react";
import React from "react";
import { Subtype } from "./Subtype";
import { EventTrackingSelector, getEventTrackingProps } from "../../../../containers/event-tracking";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import { useTranslation } from "react-i18next";

const LinkStyled = styled("a")({
    display: "flex",
    height: "100%",
    "&, &:hover": {
        color: "inherit",
        textDecoration: "inherit",
    },
});

const DocsIcon = styled(OpenInNewIcon)({
    width: 20,
    height: 20,
});

type DocsProps = PropsOf<typeof Subtype> & {
    href: string;
};

export const Docs = ({ href, ...props }: DocsProps) => {
    const { t } = useTranslation();
    return (
        <Box display="flex" justifyContent={"space-between"} alignItems={"center"} width="100%">
            <Subtype {...props} />
            <LinkStyled
                target="_blank"
                href={href}
                title="Documentation"
                rel="noreferrer"
                {...getEventTrackingProps({ selector: EventTrackingSelector.NodeDocumentation })}
            >
                <Box
                    display={"flex"}
                    height={"100%"}
                    px={1}
                    alignItems={"center"}
                    sx={{ "&:hover": { cursor: "pointer", backgroundColor: "rgba(210, 168, 255, 0.24)" } }}
                >
                    <Typography mr={0.5}>{t("docs.title", "Docs")}</Typography>
                    <DocsIcon />
                </Box>
            </LinkStyled>
        </Box>
    );
};
