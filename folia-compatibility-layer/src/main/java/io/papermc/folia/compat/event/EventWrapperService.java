package io.papermc.folia.compat.event;

import io.papermc.folia.compat.scheduler.RegionTaskDelegator;
import org.bukkit.event.Event;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;

public class EventWrapperService {
    private static final Logger LOGGER = Logger.getLogger(EventWrapperService.class.getName());

    private final RegionTaskDelegator taskDelegator;

    public EventWrapperService(RegionTaskDelegator taskDelegator) {
        if (taskDelegator == null) {
            throw new IllegalArgumentException("RegionTaskDelegator cannot be null for EventWrapperService");
        }
        this.taskDelegator = taskDelegator;
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> T wrapEvent(T originalEvent) {
        if (originalEvent == null) {
            return null;
        }

        // We need to find all interfaces implemented by the event class
        // to ensure the proxy implements all of them, making it castable.
        Class<?> eventClass = originalEvent.getClass();
        java.util.List<Class<?>> interfaces = new java.util.ArrayList<>();
        collectInterfaces(eventClass, interfaces);

        if (interfaces.isEmpty()) {
            // Should not happen for Bukkit events as they all implement at least one interface (e.g. Cancellable)
            // or extend Event which itself is not an interface.
            // However, Event subclasses themselves are what we care about (PlayerInteractEvent IF, etc.)
            LOGGER.warning("[COMPAT LAYER] Event " + eventClass.getName() + " has no interfaces to proxy. Returning original.");
            return originalEvent;
        }

        // Log interfaces being proxied
        // LOGGER.info("[COMPAT LAYER] Wrapping event " + eventClass.getName() + " with interfaces: " + interfaces.stream().map(Class::getName).collect(java.util.stream.Collectors.joining(", ")));


        GenericEventInvocationHandler<T> handler = new GenericEventInvocationHandler<>(originalEvent, taskDelegator);

        // Create a proxy that implements all interfaces of the original event class
        // and also the specific event class itself if it's not an interface (though Bukkit events usually are interfaces or extend classes that implement interfaces)
        // The classloader must be the original event's classloader.
        T proxiedEvent = (T) Proxy.newProxyInstance(
            eventClass.getClassLoader(),
            interfaces.toArray(new Class<?>[0]),
            handler
        );

        //LOGGER.fine("[COMPAT LAYER] Wrapped " + originalEvent.getEventName() + " with proxy.");
        return proxiedEvent;
    }

    private static void collectInterfaces(Class<?> clazz, java.util.List<Class<?>> interfaces) {
        if (clazz == null || clazz == Object.class) {
            return;
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            if (!interfaces.contains(iface)) {
                interfaces.add(iface);
                collectInterfaces(iface, interfaces); // Recursively add super-interfaces
            }
        }
        // Also check superclass for interfaces
        collectInterfaces(clazz.getSuperclass(), interfaces);

        // Ensure the concrete class itself is added if it's an abstract class that plugins might listen for,
        // although Bukkit events are typically listened to via their specific interfaces.
        // For safety, if clazz is not an interface itself, add it to ensure castability to its own type.
        // However, Proxy typically only works with interfaces.
        // If clazz is a concrete class, we rely on its interfaces for proxying.
        // The issue arises if a plugin listens for `Event.class` or `PlayerEvent.class` (an abstract class).
        // Bukkit's event system typically registers listeners for specific event *classes*,
        // and then checks `isAssignableFrom`.
        // The proxy will be `instanceof` the interfaces.
        // If a plugin does `if (event instanceof PlayerInteractEvent)`, this works if PlayerInteractEvent is an interface.
        // If `PlayerInteractEvent` is a class, this proxy won't be an instanceof that class directly.
        // Bukkit events like PlayerInteractEvent are indeed interfaces.
    }
}
