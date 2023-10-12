import { ProcessType } from "nussknackerUi/components/Process/types";
import { NuIcon } from "../../common";
import React from "react";
import { AccountTree, NoiseControlOff } from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import { TableCellAvatar } from "./tableCellAvatar";

export function ScenarioAvatar({ process }: { process: Pick<ProcessType, "isFragment" | "isArchived" | "state"> }) {
    const { t } = useTranslation();
    const { isFragment, isArchived, state } = process;

    return (
        <TableCellAvatar>
            {isFragment ? (
                <AccountTree
                    titleAccess={t("scenario.iconTitle", "Fragment is stateless.", {
                        context: "FRAGMENT",
                    })}
                />
            ) : state ? (
                <NuIcon
                    title={t("scenario.iconTitle", "{{tooltip}}", {
                        context: state?.status.name,
                        tooltip: state?.tooltip,
                    })}
                    color={isArchived ? "#C7C7C7" : undefined}
                    src={isArchived ? "/assets/process/archived.svg" : state.icon}
                />
            ) : (
                <NoiseControlOff sx={{ opacity: 0.1 }} />
            )}
        </TableCellAvatar>
    );
}
