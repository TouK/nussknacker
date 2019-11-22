Runtime errors
--------------

| Error type                         | Message                                           |  
| :----------------------------------|:--------------------------------------------------|
| NodeNotFoundError                  | Node $nodeId not found inside process $processId  |
| ProcessNotInitializedError         | Process $id is not initialized                    |
| WrongProcessId                     | Process has id $givenId instead of $processId     |
| UnmarshallError                    | Cannot parse saved process                        |
| ProcessIsBeingDeployedNoTestAllowed| Cannot run tests when deployment in progress. Please wait... |
| ProcessIsBeingDeployed             | Deployment is currently performed on $id by $user |
| ProcessIsBeingDeployed             | Cancel is currently performed on $id by $user |
| InvalidProcessTypeError            | Process $id is not GraphProcess |
| ProcessNotFoundError               | No process $id found            |

Process validation errors
-------------------------

| Error type                       | Message                              | Explanation                                                                   |
| :--------------------------------|:-------------------------------------| :-----------------------------------------------------------------------------|
| ExpressionParseError             | Failed to parse expression: $message | There is problem with expression in field $fieldName - it could not be parsed |
| DuplicatedNodeIds                | Duplicate node ids: $ids             | Two nodes cannot have same id                                                 |
| EmptyProcess                     | Empty process                        | Process is empty, please add some nodes                                       |
| InvalidRootNode                  | Invalid root node                    | Process can start only from source node                                       |
| InvalidTailOfBranch              | Invalid end of process               | Process branch can only end with sink or processor                            |
| MissingParameters                | Global process parameters not filled | Please fill process properties $params by clicking 'Properties button'        |
| MissingParameters                | Node parameters not filled           | Please fill missing node parameters: params                                   |
| OverwrittenVariable              | Variable $varName is already defined | Variable $varName is already defined                                          |
| UIValidation                     | Node id contains invalid characters  | " and ' are not allowed in node id                                            |

Process configuration errors (should not happen unless configuration changes are incompatible with process)
-----------------------------------------------------------------------------------------------------------

| Error type                       | Message                              | Explanation                                                      |
| :--------------------------------|:-------------------------------------| :----------------------------------------------------------------|
| NotSupportedExpressionLanguage   | Language $languageId is not supported| Currently only SPEL expressions are supported                    |
| MissingPart                      | MissingPart                          | Node $id has missing part                                        |
| WrongProcessType                 | Wrong process type                   | Process type doesn't match category - please check configuration |
| UnsupportedPart                  | UnsupportedPart                      | Type of node $id is unsupported right now                        |
| MissingCustomNodeExecutor        | Missing custom executor: $id         | Please check the name of custom executor, $id is not available   |                        |
| MissingService                   | Missing processor/enricher: $id      | Please check the name of processor/enricher, $id is not available|
| MissingSinkFactory               | Missing sink: $id                    | Please check the name of sink, $id is not available              |
| MissingSourceFactory             | Missing source: $id                  | Please check the name of source, $id is not available            |
| RedundantParameters              | Redundant parameters                 | Please omit redundant parameters: $params                        |
| WrongParameters                  | Wrong parameters                     | Please provide $params instead of $params                        |


Process warnings (not necessarily errors)
-----------------------------------------

| Error type                       | Message                              | Explanation                                                      |
| :--------------------------------|:-------------------------------------| :----------------------------------------------------------------|
| UIValidation                     | {$id} is disabled                     | Deploying process with disabled node can have unexpected consequences         |
