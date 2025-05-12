import type { SvgIconTypeMap } from "@mui/material";
import { Box, Typography } from "@mui/material";
import type { OverridableComponent } from "@mui/material/OverridableComponent";
import React from "react";
import type { FC } from "react";

interface Props {
    Icon: OverridableComponent<SvgIconTypeMap>;
    headerText: string;
    contentText: string;
}

export const ConnectionErrorContent: FC<Props> = ({ Icon, headerText, contentText }) => {
    return (
        <Box
            display={"flex"}
            alignItems={"center"}
            flexDirection={"column"}
            p={4}
            sx={(theme) => ({ backgroundColor: theme.palette.background.paper })}
        >
            <Icon sx={(theme) => ({ width: "56px", height: "56px", fill: theme.palette.text.secondary })} />
            <Typography mb={2} mt={0} variant={"h5"}>
                {headerText}
            </Typography>
            <Typography align={"center"} variant={"body2"}>
                {contentText}
            </Typography>
        </Box>
    );
};
