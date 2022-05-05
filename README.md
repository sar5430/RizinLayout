# RizinLayout

Ghidra plugin that makes function graph looks kinda like Rizin's one. This is very far for perfect, but I still find it more readable that the default graph from ghidra.

## Building

`gradle -PGHIDRA_INSTALL_DIR=/YOUR/PATH/TO/GHIDRA`

The builded zip should be located in `dist/` folder

## Installation

`File -> Install extension -> + -> builded zip`

/!\ /!\ Using this plugin with `Use Condensed Layout` option set gives pretty bad results. Just uncheck the option in `Edit -> Tool options -> Graph -> Function Graph` 

## Example 

The plugin transform that graph: 
![Screenshot 2022-05-05 at 13 06 07](https://user-images.githubusercontent.com/15121293/166920389-b673dc30-eda6-4525-a1cd-571e14ec90a3.png)

into this one:
![Screenshot 2022-05-05 at 13 06 20](https://user-images.githubusercontent.com/15121293/166920381-cf974275-a133-4058-b3d9-900ab30f2670.png)

