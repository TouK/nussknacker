import type { ProcessStateType } from "../../components/Process/types";
import type { RootState } from "../index";

export const getProcessState = (state: RootState): ProcessStateType | null => state.scenarioState;
