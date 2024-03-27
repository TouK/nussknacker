import React from "react";
import { useTranslation } from "react-i18next";
import Date from "../common/Date";
import { PredefinedActionType, ProcessVersionType } from "../Process/types";
import { HistoryItemStyled, StyledBadge } from "./StyledHistory";
import WarningAmber from "@mui/icons-material/WarningAmber";
import { Box, Typography } from "@mui/material";

type HistoryItemProps = {
    isLatest?: boolean;
    isDeployed?: boolean;
    version: ProcessVersionType;
    onClick: (version: ProcessVersionType) => void;
    type: VersionType;
};

export enum VersionType {
    current,
    past,
    future,
}

const mapVersionToClassName = (v: VersionType): string => {
    switch (v) {
        case VersionType.current:
            return "current";
        case VersionType.past:
            return "past";
        case VersionType.future:
            return "future";
    }
};

const HDate = ({ date }: { date: string }) => (
    <small>
        <i>
            <Date date={date} />
        </i>
    </small>
);

export function HistoryItem({ onClick, version, type, isLatest, isDeployed }: HistoryItemProps): JSX.Element {
    const { t } = useTranslation();
    const { user, createDate, processVersionId, actions } = version;

    return (
        <HistoryItemStyled className={mapVersionToClassName(type)} type={type} onClick={() => onClick(version)}>
            <Typography component={"div"} variant={"caption"}>
                {`v${processVersionId}`} | {user}
                {isLatest && !isDeployed && (
                    <Box
                        title={t("processHistory.lastVersionIsNotDeployed", "Last version is not deployed")}
                        sx={{ display: "inline-flex", verticalAlign: "middle" }}
                    >
                        <WarningAmber sx={{ margin: "0 2px 1px", fontSize: "small" }} />
                    </Box>
                )}
                <br />
                <HDate date={createDate} />
                <br />
                {isDeployed && <HDate date={actions.find((a) => a.actionName === PredefinedActionType.Deploy)?.performedAt} />}
            </Typography>
            {isDeployed && <StyledBadge />}
        </HistoryItemStyled>
    );
}
