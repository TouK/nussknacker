export type DialogType = "INFO_MODAL"
  | "PROCESS_ACTION"
  | "SAVE_PROCESS"
  | "GENERATE_TEST_DATA"
  | "CALCULATE_COUNTS"
  | "COMPARE_VERSIONS"

export const dialogTypesMap: Record<string, DialogType> = {
  infoModal: "INFO_MODAL",
  processAction: "PROCESS_ACTION",
  saveProcess: "SAVE_PROCESS",
  generateTestData: "GENERATE_TEST_DATA",
  calculateCounts: "CALCULATE_COUNTS",
  compareVersions: "COMPARE_VERSIONS",
}
