# Building Dimension

## Setup

This mod requires [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) to be installed.  
It was tested with Minecraft 1.20.1 and Fabric Loader 0.14.22.  
This mod is required only on the server side.  

## Description

This mod allows you to switch between any dimension to its respective creative world.  
These dimensions can be used to prepare builds, farms, etc. without affecting the main world.  

## Commands

`/creative` allows you to switch between the dimension and its respective creative world  
`/creative teleport ‹player›` allows you to teleport to a player in the same creative world as you  
`/creative sync ‹radius›` allows you to synchronise chunks from the dimension to its respective creative world  

## Restrictions 

To prevent the Creative World from being used to cheat, the following restrictions are in place.  
Inventories are switched to its respective world inventory.  
The following are only saved for the overworld:  
- Experience  
- Ender Chest Inventory  
- Position when entering the creative world  
- Effects
- Achievements
- [Trinkets](https://www.curseforge.com/minecraft/mc-mods/trinkets) (if the mod is present) 

Portals such as for the Nether or the End are disabled.  
But other types of portals from other mods are not.

## Configuration

A configuration file is generated in the `config` folder of your Server or Client.  
You can find inside it the options described with their default values.  
If after an update you don't see the new options, you can store the file under another name, and it will be regenerated with the new options.  

## Issues and Bugs

- [ ] Trees and plants seems to not be growing at the exact same place in both worlds  
- [ ] Players don't receive light update when synchronising chunks (No issues server side)

## Future

- [ ] Add more configuration options  
- [ ] Support more mods that add slots to the player inventory (idk which one exists)
- [ ] Try to block any portal from any mod (needs testing)
- [ ] Further improvements to chunk synchronisation
- [ ] Update to Minecraft 1.20.2 and maybe try to add for earlier versions
- [ ] Better README.md and mod picture