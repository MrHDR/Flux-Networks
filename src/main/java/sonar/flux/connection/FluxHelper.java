package sonar.flux.connection;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import sonar.core.api.energy.EnergyType;
import sonar.core.api.utils.ActionType;
import sonar.core.utils.Pair;
import sonar.flux.FluxConfig;
import sonar.flux.FluxNetworks;
import sonar.flux.api.AdditionType;
import sonar.flux.api.RemovalType;
import sonar.flux.api.energy.IItemEnergyHandler;
import sonar.flux.api.energy.ITileEnergyHandler;
import sonar.flux.api.network.IFluxNetwork;
import sonar.flux.api.tiles.IFlux;
import sonar.flux.api.tiles.IFluxController.PriorityMode;
import sonar.flux.api.tiles.IFluxController.TransferMode;
import sonar.flux.api.tiles.IFluxListenable;
import sonar.flux.api.tiles.IFluxPlug;
import sonar.flux.api.tiles.IFluxPoint;
import sonar.flux.network.FluxNetworkCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public class FluxHelper {

	public static void addConnection(IFluxListenable flux, AdditionType type) {
		FluxNetworkCache.instance().getListenerList().addSubListenable(flux);
		if (flux.getNetworkID() != -1) {
			IFluxNetwork network = FluxNetworks.getServerCache().getNetwork(flux.getNetworkID());
			if (!network.isFakeNetwork()) {
				network.addConnection(flux, type);
			}
		}
	}

	public static void removeConnection(IFluxListenable flux, RemovalType type) {
		FluxNetworkCache.instance().getListenerList().removeSubListenable(flux);
		if (flux.getNetworkID() != -1) {
			IFluxNetwork network = FluxNetworks.getServerCache().getNetwork(flux.getNetworkID());
			if (!network.isFakeNetwork()) {
				network.removeConnection(flux, type);
			}
		}
	}

	public static UUID getOwnerUUID(EntityPlayer player) {
		return player.getGameProfile().getId();
	}

	public static boolean isPlayerAdmin(EntityPlayer player) {
		return player.isCreative();
	}

	public static void sortConnections(List<IFlux> flux, PriorityMode mode) {
		switch (mode) {
		case DEFAULT:
			break;
		case LARGEST:
			flux.sort((o1, o2) -> o2.getCurrentPriority() - o1.getCurrentPriority());
			break;
		case SMALLEST:
			flux.sort(Comparator.comparingInt(IFlux::getCurrentPriority));
			break;
		default:
			break;
		}
	}

	public static long transferEnergy(IFluxPlug plug, List<IFluxPoint> points, EnergyType type, TransferMode mode) {
		long currentLimit = Long.MAX_VALUE;
		
		for (IFluxPoint point : points) {
			if (currentLimit <= 0) {
				break;
			}
			if (point.getConnectionType() != plug.getConnectionType()) {// storages can be both
				long toTransfer = addEnergyToNetwork(plug, type, removeEnergyFromNetwork(point, type, currentLimit, ActionType.SIMULATE), ActionType.SIMULATE);
				if (toTransfer > 0) {
					long pointRec = removeEnergyFromNetwork(point, type, toTransfer, ActionType.PERFORM);
					currentLimit -= addEnergyToNetwork(plug, type, pointRec, ActionType.PERFORM);
				}
			}
		}
		
		return Long.MAX_VALUE - currentLimit;
	}

	public static long addEnergyToNetwork(IFlux from, EnergyType type, long maxTransferRF, ActionType actionType) {
		return from.getTransferHandler().addToNetwork(maxTransferRF, type, actionType);
	}

	public static long removeEnergyFromNetwork(IFlux from, EnergyType type, long maxTransferRF, ActionType actionType) {
		return from.getTransferHandler().removeFromNetwork(maxTransferRF, type, actionType);
	}

	/* @Deprecated public static long pullEnergy(IFlux from, long maxTransferRF, ActionType actionType) { long extracted = 0; maxTransferRF = Math.min(maxTransferRF, from.getCurrentTransferLimit()); if (from != null && maxTransferRF != 0) { switch (from.getConnectionType()) { case PLUG: extracted += from.getTransferHandler().addToNetwork(maxTransferRF - extracted, actionType); break; case STORAGE: break; default: break; } } return extracted; }
	 * @Deprecated public static long pushEnergy(IFlux to, long maxTransferRF, ActionType actionType) { long received = 0; maxTransferRF = Math.min(maxTransferRF, to.getCurrentTransferLimit()); if (to != null && maxTransferRF != 0 && to.hasTransfers()) { switch (to.getConnectionType()) { case POINT: received += to.getTransferHandler().removeFromNetwork(maxTransferRF - received, actionType); break; case STORAGE: break; case CONTROLLER: break; default: break; } } return received; } */

	public static boolean canConnect(TileEntity tile, EnumFacing dir) {
		return tile != null && !(tile instanceof IFlux) && getValidHandler(tile, dir) != null;// || SonarLoader.rfLoaded && tile instanceof IEnergyConnection && FluxConfig.transfers.get(EnergyType.RF).a);
	}

	public static List<ITileEnergyHandler> getTileEnergyHandlers() {
		List<ITileEnergyHandler> handlers = new ArrayList<>();
		for (ITileEnergyHandler handler : FluxNetworks.loadedTileEnergyHandlers) {
			Pair<Boolean, Boolean> canTransfer = FluxConfig.transfer_types.get(handler.getEnergyType());
			if (canTransfer!=null && canTransfer.a) {
				handlers.add(handler);
			}
		}
		return handlers;
	}

	public static List<IItemEnergyHandler> getItemEnergyHandlers() {
		List<IItemEnergyHandler> handlers = new ArrayList<>();
		for (IItemEnergyHandler handler : FluxNetworks.loadedItemEnergyHandlers) {
			Pair<Boolean, Boolean> canTransfer = FluxConfig.transfer_types.get(handler.getEnergyType());
			if (canTransfer!=null && canTransfer.a) {
				handlers.add(handler);
			}
		}
		return handlers;
	}

	public static ITileEnergyHandler getValidHandler(TileEntity tile, EnumFacing dir) {
		if(tile == null || tile instanceof IFlux){
			return null;
		}
		List<ITileEnergyHandler> handlers = FluxNetworks.enabledTileEnergyHandlers;
		for (ITileEnergyHandler handler : handlers) {
			if (handler.canRenderConnection(tile, dir)) {
				return handler;
			}
		}
		return null;
	}

	public static IItemEnergyHandler getValidAdditionHandler(ItemStack stack) {
		return getValidHandler(stack, t -> t.canAddEnergy(stack));
	}

	public static IItemEnergyHandler getValidRemovalHandler(ItemStack stack) {
		return getValidHandler(stack, t -> t.canRemoveEnergy(stack));
	}

	public static IItemEnergyHandler getValidHandler(ItemStack stack, Predicate<IItemEnergyHandler> filter) {
		if (stack.isEmpty()) {
			return null;
		}
		List<IItemEnergyHandler> handlers = FluxNetworks.enabledItemEnergyHandlers;
		for (IItemEnergyHandler handler : handlers) {
			if (filter.test(handler)) {
				return handler;
			}
		}
		return null;
	}
}
