import { ProcessType } from "nussknackerUi/components/Process/types";
import { NuIcon } from "../../common";
import React from "react";
import { AccountTree, NoiseControlOff } from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import { TableCellAvatar } from "./tableCellAvatar";

export function ScenarioAvatar({ process }: { process: Pick<ProcessType, "isSubprocess" | "state"> }) {
    const { t } = useTranslation();
    const { isSubprocess, state } = process;
    return (
        <TableCellAvatar>
            {isSubprocess ? (
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
                    src={state.icon}
                />
            ) : (
                <NoiseControlOff sx={{ opacity: 0.1 }} />
            )}
        </TableCellAvatar>
    );
}
