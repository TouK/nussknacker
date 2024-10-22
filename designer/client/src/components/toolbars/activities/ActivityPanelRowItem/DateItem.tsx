import React, { ForwardedRef, forwardRef } from "react";
import { Box, Divider, Typography } from "@mui/material";
import { formatUiDate } from "../helpers/date";
import { DateActivity } from "../ActivitiesPanel";

interface Props {
    activity: DateActivity;
    isFirstDateItem: boolean;
}
export const DateItem = forwardRef(({ activity, isFirstDateItem }: Props, ref: ForwardedRef<HTMLDivElement>) => {
    return (
        <Box display={"flex"} justifyContent={"center"} alignItems={"center"} px={1} pt={!isFirstDateItem && 3} pb={1} ref={ref}>
            <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.common.white, mr: 1 })} />
            <Typography component={"div"} variant={"caption"}>
                {Array.isArray(activity.value)
                    ? `${formatUiDate(activity.value[0])} - ${formatUiDate(activity.value[1])}`
                    : formatUiDate(activity.value)}
            </Typography>
            <Divider variant={"fullWidth"} sx={(theme) => ({ flex: 1, backgroundColor: theme.palette.common.white, ml: 1 })} />
        </Box>
    );
});

DateItem.displayName = "DateItem";
