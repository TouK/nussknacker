import React, { ForwardedRef, forwardRef } from "react";
import { Box, Divider, Typography } from "@mui/material";
import { formatUiDate } from "../helpers/date";
import { DateActivity } from "../ActivitiesPanel";

interface Props {
    activity: DateActivity;
}
export const DateItem = forwardRef(({ activity }: Props, ref: ForwardedRef<HTMLDivElement>) => {
    return (
        <Box display={"flex"} justifyContent={"center"} alignItems={"center"} px={1}>
            <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.common.white, mr: 1 })} />
            <Typography component={"div"} variant={"caption"} ref={ref}>
                {Array.isArray(activity.value)
                    ? `${formatUiDate(activity.value[0])} - ${formatUiDate(activity.value[1])}`
                    : formatUiDate(activity.value)}
            </Typography>
            <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.common.white, ml: 1 })} />
        </Box>
    );
});

DateItem.displayName = "DateItem";
