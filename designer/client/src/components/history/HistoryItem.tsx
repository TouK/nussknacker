import React from "react";
import Date from "../common/Date";
import { ActionType, ProcessVersionType } from "../Process/types";
import { HistoryItemStyled, StyledBadge } from "./StyledHistory";
import WarningAmber from "@mui/icons-material/WarningAmber";
import { Box } from "@mui/material";

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
    const { user, createDate, processVersionId, actions } = version;

    return (
        <HistoryItemStyled className={mapVersionToClassName(type)} type={type} onClick={() => onClick(version)}>
            <div>
                {`v${processVersionId}`} | {user}
                {isLatest && !isDeployed && (
                    <Box sx={{ display: "inline-flex", verticalAlign: "middle" }}>
                        <WarningAmber sx={{ margin: "0 3px", fontSize: "small" }} />
                    </Box>
                )}
                <br />
                <HDate date={createDate} />
                <br />
                {isDeployed && <HDate date={actions.find((a) => a.actionType === ActionType.Deploy)?.performedAt} />}
            </div>
            {isDeployed && <StyledBadge />}
        </HistoryItemStyled>
    );
}
