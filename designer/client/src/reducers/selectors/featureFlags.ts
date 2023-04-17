import {FeatureFlags} from "../featureFlags"
import {RootState} from "../index"

export const featureFlags = (state: RootState): FeatureFlags => state.featureFlags
