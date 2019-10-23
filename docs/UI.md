#UI

This section describes basic functionalities of user interface.

#Processes
Features
- list of editable and custom non-editable processes
- search 
- filter by category
- process status indication - if it's running processes have green status indicator, 
if process crashed there will be red status indicator
- edit action
- link to process' metric dashboard 

#Subprocesses
- list of editable and custom non-editable subprocesses
- search 
- filter by category
- edit action

#Process editing
##Left panel
- creator panel - with all nodes available for chosen process category. Just drag & drop it to create process 
- versions - every saved version of process is persisted
- comments
- attachments

##Right panel
- business view - enable to view simpler version of process without variable and enricher nodes

###Deployment
- deploy - deploy process to runtime engine (e.g. Flink, Standalone)
- cancel - cancel the process (e.g. Flink cancel)
- metrics - see metrics in Grafana

###Process
- save - save the process, add optional comment with change summary
- migrate - migrate process between environments
- compare - compare two versions of the process - see which nodes were added/deleted or see how node's properties changed
- import - import process from json file
- JSON - export process to json file
- PDF - export process graph to PDF with all process details
- zoomIn/zoomOut
- archive

###Edit
- undo/redo - undo/redo graph process actions
- align - automatically layout the process
- properties - edit properties of the process
- duplicate - duplicate node 
- copy/cut/delete/paste - editing operations, they can work on sets of nodes

###Test
- from file - test process from file input
- hide - hide test results
- generate - fetch test data from process source and save to a file
- counts - see how many events went through each node during given time period

###Group
- start - start grouping and mark nodes to group
- finish - finish grouping
- cancel
- ungroup
