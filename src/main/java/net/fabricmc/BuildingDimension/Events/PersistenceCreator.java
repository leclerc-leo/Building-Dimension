package net.fabricmc.BuildingDimension.Events;

import net.fabricmc.BuildingDimension.Persistance.PersistenceManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class PersistenceCreator {

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(PersistenceManager::getSavedData);
    }
}
