This mod allows you to switch between any dimension to its respective building world.
These dimensions can be used to prepare builds, farms, etc. without affecting the main world.

# Installation Requirements

[<img src="https://i.imgur.com/Ol1Tcf8.png" width="150px" alt="Fabric-API">](https://www.curseforge.com/minecraft/mc-mods/fabric-api)

This mod runs on [Fabric](https://fabricmc.net/) only and is only required on the server.

# Commands

<table>
    <tbody>
        <tr>
            <td><code>/building</code></td>
            <td>Switch between the dimension and its respective building world</td>
        </tr>
        <tr>
            <td><code>/building teleport ‹player›</code></td>
            <td>Teleport to a player in the same building world as you</td>
        </tr>
        <tr>
            <td><code>/building sync ‹radius›</code></td>
            <td>Synchronise chunks from the dimension to its respective building world</td>
        </tr>
    </tbody>
</table>

# Restrictions 

To prevent the Building World from being used to cheat, the following restrictions are in place.  
Inventories are switched to its respective world inventory.  
The following are only saved for the overworld:  
- Experience  
- Ender Chest Inventory  
- Position when entering the building world  
- Effects
- Achievements
- [Trinkets](https://www.curseforge.com/minecraft/mc-mods/trinkets) (if the mod is present) 

Portals from other mods are blocked when being in the building dimension, but you can still use the `/building` command to leave.

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
- [ ] Update to Minecraft 1.20.4 and maybe try to add for earlier versions
- [ ] Better README.md and mod picture

