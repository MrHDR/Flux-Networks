package sonar.flux.connection.transfer.handlers;

import com.google.common.collect.Lists;
import net.minecraft.util.EnumFacing;
import sonar.core.api.energy.EnergyType;
import sonar.core.api.utils.ActionType;
import sonar.flux.api.energy.internal.IEnergyTransfer;
import sonar.flux.api.energy.internal.IFluxTransfer;
import sonar.flux.api.energy.internal.ITransferHandler;
import sonar.flux.api.tiles.IFlux;
import sonar.flux.connection.NetworkSettings;

import java.util.List;

public class SingleTransferHandler extends FluxTransferHandler implements ITransferHandler {

	public final IEnergyTransfer transfer;

	public SingleTransferHandler(IFlux flux, IEnergyTransfer transfer) {
		super(flux);
		this.transfer = transfer;
	}

	@Override
	public long addToNetwork(long maxTransferRF, EnergyType energyType, ActionType actionType) {
		if (flux.isActive() && getNetwork().canConvert(energyType, getNetwork().getSetting(NetworkSettings.NETWORK_ENERGY_TYPE))) {
			long add = transfer.addToNetworkWithConvert(getValidAddition(maxTransferRF, energyType), energyType, actionType);
			if (!actionType.shouldSimulate()) {
				this.added += add;
			}
			return add;
		}
		return 0;
	}

	@Override
	public long removeFromNetwork(long maxTransferRF, EnergyType energyType, ActionType actionType) {
		if (flux.isActive() && getNetwork().canConvert(energyType, getNetwork().getSetting(NetworkSettings.NETWORK_ENERGY_TYPE))) {
			long remove = transfer.removeFromNetworkWithConvert(getValidRemoval(maxTransferRF, energyType), energyType, actionType);
			if (!actionType.shouldSimulate()) {
				this.removed += remove;
			}
			return remove;
		}
		return 0;
	}

	@Override
	public boolean hasTransfers() {
		return true;
	}

	@Override
	public void updateTransfers(EnumFacing... faces) {}

	@Override
	public List<IFluxTransfer> getTransfers() {
		return Lists.newArrayList(transfer);
	}

}