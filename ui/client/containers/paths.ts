export const RootPath = `/`
export const ProcessesTabDataPath = `/processes`
export const ArchiveTabDataPath = `/processes/archived`
export const SubProcessesTabDataPath = `/processes/subprocesses`
export const ProcessesLegacyPaths = [ProcessesTabDataPath, SubProcessesTabDataPath, ArchiveTabDataPath]
export const VisualizationBasePath = `/visualization`
export const VisualizationPath = `${VisualizationBasePath}/:processId`
export const MetricsBasePath = `/metrics`
export const MetricsPath = `${MetricsBasePath}/:processId?`
export const SignalsPath = `/signals`
export const AdminPagePath = `/admin`
export const CustomTabBasePath = `/customtabs`
export const ScenariosBasePath = `${CustomTabBasePath}/scenarios`

