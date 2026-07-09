package net.buildingdimension.platform;

import net.buildingdimension.Constants;
import net.buildingdimension.platform.services.IAccessoryHelper;
import net.buildingdimension.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

// Service loaders are a built-in Java feature that allow us to locate implementations of an interface that vary from one
// environment to another. In the context of MultiLoader we use this feature to access a mock API in the common code that
// is swapped out for the platform specific implementation at runtime.
public class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final IAccessoryHelper ACCESSORIES = load(IAccessoryHelper.class);

    public static <T> T load(Class<T> clazz) {

        final T loadedService = ServiceLoader.load(clazz, Services.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
