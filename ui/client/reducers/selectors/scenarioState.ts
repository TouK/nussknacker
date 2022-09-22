import {RootState} from "../index"
import {ProcessStateType} from "../../components/Process/types";

export const isProcessStateLoaded = (state: RootState): boolean => !!state.scenarioState?.processStateLoaded
export const getProcessState =  (state: RootState): ProcessStateType|undefined => state.scenarioState?.processState