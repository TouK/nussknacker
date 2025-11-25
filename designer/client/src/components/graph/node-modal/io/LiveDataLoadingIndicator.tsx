import { Fade, Skeleton, Stack, Typography } from "@mui/material";
import React, { memo } from "react";

import { getLiveDataNextUpdate } from "../../../../reducers/selectors/getLiveData";
import { useAppSelector } from "../../../../store/storeHelpers";

export const LiveDataLoadingIndicator = memo(function ContextAccordion({ noLabel }: { noLabel?: boolean }) {
    const isLiveDataWorking = useAppSelector(getLiveDataNextUpdate);
    return (
        <Fade in={Boolean(isLiveDataWorking)} mountOnEnter unmountOnExit>
            <Stack
                direction="row"
                p={1}
                spacing={0.5}
                sx={{
                    alignItems: "center",
                    justifyContent: "flex-end",
                    position: "sticky",
                    top: 0,
                    right: 0,
                    zIndex: 10,
                    filter: "drop-shadow(0 0 2px black)",
                    height: "36px",
                    marginTop: "-36px",
                }}
            >
                <Skeleton
                    variant="circular"
                    sx={(theme) => ({ width: "1em", height: "1em", backgroundColor: theme.palette.success.main })}
                />
                {noLabel ? null : <Typography variant="caption">LIVE</Typography>}
            </Stack>
        </Fade>
    );
});
