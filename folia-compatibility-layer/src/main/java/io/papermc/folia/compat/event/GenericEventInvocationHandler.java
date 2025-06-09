package io.papermc.folia.compat.event;

import io.papermc.folia.compat.scheduler.RegionTaskDelegator;
import io.papermc.folia.compat.wrappers.WrappedBlock;
import io.papermc.folia.compat.wrappers.WrappedWorld;
import io.papermc.folia.compat.wrappers.entity.WrappedEntity;
import io.papermc.folia.compat.wrappers.entity.WrappedLivingEntity;
import io.papermc.folia.compat.wrappers.entity.WrappedPlayer;
import io.papermc.folia.compat.wrappers.inventory.WrappedInventory;
import io.papermc.folia.compat.wrappers.inventory.WrappedPlayerInventory;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

public class GenericEventInvocationHandler<T extends Event> implements InvocationHandler {
    private static final Logger LOGGER = Logger.getLogger(GenericEventInvocationHandler.class.getName());

    private final T originalEvent;
    private final RegionTaskDelegator taskDelegator;

    // Simple cache for wrapped objects to return the same wrapper instance for the same real object within the event's scope
    private final Map<Object, Object> wrappedCache = new HashMap<>();


    public GenericEventInvocationHandler(T originalEvent, RegionTaskDelegator taskDelegator) {
        this.originalEvent = originalEvent;
        this.taskDelegator = taskDelegator;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // LOGGER.fine("Invoked method on wrapped event: " + method.getName() + " on " + originalEvent.getEventName());

        // Always delegate these fundamental methods to the original event
        if (method.getName().equals("hashCode")) {
            return originalEvent.hashCode();
        }
        if (method.getName().equals("equals") && args != null && args.length == 1) {
            Object arg = args[0];
            // If comparing with another proxy, unwrap it first
            if (Proxy.isProxyClass(arg.getClass())) {
                InvocationHandler otherHandler = Proxy.getInvocationHandler(arg);
                if (otherHandler instanceof GenericEventInvocationHandler) {
                    arg = ((GenericEventInvocationHandler<?>) otherHandler).originalEvent;
                }
            }
            return originalEvent.equals(arg);
        }
        if (method.getName().equals("toString")) {
            return "WrappedEvent$" + originalEvent.toString();
        }
        if (method.getName().equals("getEventName")) {
            return originalEvent.getEventName();
        }
        if (method.getName().equals("isAsynchronous")) {
            return originalEvent.isAsynchronous();
        }


        // Proof-of-concept for PlayerInteractEvent
        if (originalEvent instanceof PlayerInteractEvent) {
            PlayerInteractEvent pie = (PlayerInteractEvent) originalEvent;
            switch (method.getName()) {
                case "getPlayer":
                    return wrapPlayer(pie.getPlayer());
                case "getClickedBlock":
                    Block clickedBlock = pie.getClickedBlock();
                    return clickedBlock != null ? wrapBlock(clickedBlock) : null;
                case "getItem":
                    ItemStack item = pie.getItem();
                    return item != null ? item.clone() : null; // Clone ItemStacks
                // Other PlayerInteractEvent methods would go here
            }
        }

        // General wrapping logic based on return type (can be expanded)
        Object realReturnValue = method.invoke(originalEvent, args);

        if (realReturnValue == null) {
            return null;
        }

        return wrapObject(realReturnValue);
    }

    private Object wrapObject(Object realObject) {
        if (realObject == null) return null;
        if (wrappedCache.containsKey(realObject)) return wrappedCache.get(realObject);

        Object wrapped = realObject; // Default to returning the real object if no specific wrapper
        if (realObject instanceof Player) {
            wrapped = wrapPlayer((Player) realObject);
        } else if (realObject instanceof LivingEntity) { // Must be before Entity
            wrapped = new WrappedLivingEntity((LivingEntity) realObject, taskDelegator);
        } else if (realObject instanceof Entity) {
            wrapped = new WrappedEntity((Entity) realObject, taskDelegator);
        } else if (realObject instanceof Block) {
            wrapped = wrapBlock((Block) realObject);
        } else if (realObject instanceof World) {
            // This world needs the task delegator. The delegator is available.
            wrapped = new WrappedWorld((World) realObject, taskDelegator);
        } else if (realObject instanceof PlayerInventory) {
            // PlayerInventory holder is a Player. We need to ensure that player is wrapped.
            PlayerInventory pi = (PlayerInventory) realObject;
            if (pi.getHolder() instanceof Player) {
                WrappedPlayer wrappedPlayer = wrapPlayer((Player) pi.getHolder());
                wrapped = new WrappedPlayerInventory(pi, taskDelegator, wrappedPlayer);
            } else {
                 LOGGER.warning("[COMPAT LAYER] PlayerInventory holder is not a Player? " + pi.getHolder());
                 // Fallback to generic inventory wrapper if possible, or don't wrap.
            }
        } else if (realObject instanceof Inventory) {
             // Generic inventory. Holder could be anything. This is tricky without context.
             // For now, we might not wrap generic inventories unless we can determine their holder type.
             LOGGER.fine("[COMPAT LAYER] Returning non-wrapped Inventory for " + realObject.getClass().getName());
        } else if (realObject instanceof ItemStack) {
            wrapped = ((ItemStack) realObject).clone();
        } else if (realObject instanceof Location) {
            wrapped = ((Location) realObject).clone(); // Locations are mutable, clone them.
        }
        // Add more types as needed: Chunk, etc.

        if (wrapped != realObject) { // If we actually wrapped it
             wrappedCache.put(realObject, wrapped);
        }
        return wrapped;
    }

    private WrappedPlayer wrapPlayer(Player realPlayer) {
        if (realPlayer == null) return null;
        return (WrappedPlayer) wrappedCache.computeIfAbsent(realPlayer, p -> new WrappedPlayer((Player)p, taskDelegator));
    }

    private WrappedBlock wrapBlock(Block realBlock) {
        if (realBlock == null) return null;
        return (WrappedBlock) wrappedCache.computeIfAbsent(realBlock, b -> {
            // WrappedBlock needs WrappedWorld. We get the world from the real block and wrap it.
            WrappedWorld wrappedWorld = (WrappedWorld) wrapObject(realBlock.getWorld());
            return new WrappedBlock((Block)b, taskDelegator, wrappedWorld);
        });
    }
}
