/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import core.Settings;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class CustomEpidemicRouter extends ActiveRouter {
	public static final String CE_NS = "CERouter";
	public static final String THRESHOLD_ENERGY ="thresholdEnergy";
	public static int thresholdEnergy;
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public CustomEpidemicRouter(Settings s) {
		super(s);
		Settings ceSettings = new Settings(CE_NS);
		thresholdEnergy = ceSettings.getInt(THRESHOLD_ENERGY);
		//TODO: read&use epidemic router specific settings (if any)
	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected CustomEpidemicRouter(CustomEpidemicRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
	}

	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
				
		if (! hasSufficientEnergy()) {
			return;
		}
		
		// then try any/all message to any/all connection
		this.tryAllMessagesToAllConnections();
	}


	@Override
	public CustomEpidemicRouter replicate() {
		return new CustomEpidemicRouter(this);
	}
	
	public boolean hasSufficientEnergy() {
		if (this.hasEnergy()) {
			
			if (this.getEnergy().getEnergy() < thresholdEnergy) {
				System.out.println("Insufficient Energy! Value = " + this.getEnergy().getEnergy());
				return false;
			} else {
				System.out.println("Sufficient Energy! Value = " + this.getEnergy().getEnergy());
				return true;
			}
		} 
		return false;
	}

}
