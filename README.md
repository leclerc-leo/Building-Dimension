# Building Dimension

## Setup

This mod requires [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api) to be installed.  
It was tested with Minecraft 1.19.2 and Fabric Loader 0.14.13.

## Description

This adds a new dimension named the Creative World.  
It is a dimension where you can build freely without any restrictions.  
You can then create schematics of your builds to build them alter in the overworld.

## Commands

`/creative` allows you to switch between dimension  
`/creative teleport ‹player›` allows you to teleport to a player in the Creative World  
`/creative sync ‹radius›` allows you to synchronise chunks from the overworld to the Creative World

## Restrictions 

To prevent the Creative World from being used to cheat, the following restrictions are in place.  
Inventories are switched to its respective world inventory.  
The following are only saved for the overworld:
- Experience
- Ender Chest Inventory
- Position when entering the Creative World
- Effects
- Advancements (they will be rolled back when leaving the Creative World)

## Future

- [ ] Optimise the chunk synchronisation