import { ProcessingMode } from "../../../http/HttpService";
import i18next from "i18next";

export const getProcessingModeVariantName = (processingMode: ProcessingMode) => {
    switch (processingMode) {
        case ProcessingMode.batch: {
            return i18next.t(`scenarioDetails.processingModeVariants.batch`, "Batch");
        }

        case ProcessingMode.requestResponse: {
            return i18next.t(`scenarioDetails.processingModeVariants.requestResponse`, "Request-Response");
        }
        case ProcessingMode.streaming:
            return i18next.t(`scenarioDetails.processingModeVariants.streaming`, "Streaming");
    }
};
