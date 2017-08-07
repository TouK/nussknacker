#UI

This section describes basic functionalities of user interface.

#Processes tab
Features
- list of editable and custom non-editable processes and subprocesses
- deploy/stop process
- search by process id or category
- check process status - if it's running on Flink cluster there will be green status indicator, 
if process crashed there will be red status indicator

#Process visualization
##Left panel
- toolbox - creator panel with all nodes available for process category. Just drag & drop it to create process 
- History - every version of process is persisted
- comments
- attachments

##User right panel buttons
- business view - enable to view simpler version of process without variable and enricher nodes
###Deployment
- deploy - deploy process to Flink cluster
- stop - stop Flink's process
- metrics - see metrics in Grafana
###Process
- save - save process, add optional comment with change summary
- migrate - migrate process between environments
- compare - compare two version of processes - see what nodes were added/deleted or see how node changed
- import - import process from json file
- export - export process to json file
- exportPDF - export process graph to PDF with all process details
- zoomIn/zoomOut
###Edit
- undo/redo - undo/redo graph process actions
- align - automatically center process
- properties - edit global process properties
- duplicate - duplicate node 
- delete - delete node
###Test
- from file - test process from file input
- hide - hide test results
- generate - fetch test data from process source and save to file
- counts - see how many events went through each node during given time period
###Group
- start - start grouping and mark nodes to group
- finish - finish grouping
- cancel
- ungroup

###Testing

##Editing graph

###Editing nodes


