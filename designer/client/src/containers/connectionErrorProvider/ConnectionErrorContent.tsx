import React, { FC } from "react";
import { Box, SvgIconTypeMap, Typography } from "@mui/material";
import { OverridableComponent } from "@mui/material/OverridableComponent";

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
