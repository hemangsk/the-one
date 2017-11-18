/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import core.Settings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import core.DTNHost;
import core.Message;
import routing.util.RoutingInfo;
import util.Tuple;
import core.Connection;
import core.Settings;
import core.SimClock;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class CustomEpidemicRouter extends ActiveRouter {
	/** delivery predictability initialization constant*/
	public static final double P_INIT = 0.75;
	/** delivery predictability transitivity scaling constant default value */
	public static final double DEFAULT_BETA = 0.25;
	/** delivery predictability aging constant */
	public static final double GAMMA = 0.98;
	
	/**
	 * Number of seconds in time unit -setting id ({@value}).
	 * How many seconds one time unit is when calculating aging of
	 * delivery predictions. Should be tweaked for the scenario.*/
	public static final String SECONDS_IN_UNIT_S ="secondsInTimeUnit";

	/**
	 * Transitivity scaling constant (beta) -setting id ({@value}).
	 * Default value for setting is {@link #DEFAULT_BETA}.
	 */
	public static final String BETA_S = "beta";

	/** the value of nrof seconds in time unit -setting */
	private int secondsInTimeUnit;
	/** value of beta setting */
	private double beta;

	/** delivery predictabilities */
	private Map<DTNHost, Double> preds;
	/** last delivery predictability update (sim)time */
	private double lastAgeUpdate;
	
	
	public static final String CE_NS = "CERouter";
	public static final String THRESHOLD_ENERGY ="thresholdEnergy";
	public static final String THRESHOLD_PROBABILITY = "thresholdProbability";
	public static int thresholdEnergy;
	public static double thresholdProbability;
	
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public CustomEpidemicRouter(Settings s) {
		super(s);
		
		Settings ceSettings = new Settings(CE_NS);
		
		secondsInTimeUnit = ceSettings.getInt(SECONDS_IN_UNIT_S);
		if (ceSettings.contains(BETA_S)) {
			beta = ceSettings.getDouble(BETA_S);
		}
		else {
			beta = DEFAULT_BETA;
		}

		initPreds();
		
		thresholdEnergy = ceSettings.getInt(THRESHOLD_ENERGY);
		thresholdProbability = ceSettings.getDouble(THRESHOLD_PROBABILITY);
		
		//TODO: read&use epidemic router specific settings (if any)
	}
	
	
	

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected CustomEpidemicRouter(CustomEpidemicRouter r) {
		super(r);
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		this.beta = r.beta;
		initPreds();
		//TODO: copy epidemic settings here (if any)
	}
	
	private void initPreds() {
		this.preds = new HashMap<DTNHost, Double>();
	}
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		if (this.getEnergy() != null && con.isUp() && !con.isInitiator(getHost())) {
			this.getEnergy().reduceDiscoveryEnergy();
		}
		
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			updateDeliveryPredFor(otherHost);
			updateTransitivePreds(otherHost);
		}
	}
	
	
	public int getMessageSize()
	{   
		return Message.getSize1();
	}
	
	
	
	/**
	 * Updates delivery predictions for a host.
	 * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
	 * @param host The host we just met
	 */
	private void updateDeliveryPredFor(DTNHost host) {
		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * P_INIT;
		preds.put(host, newValue);
	}

	/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 * @param host The host to look the P for
	 * @return the current P value
	 */
	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (preds.containsKey(host)) {
			return preds.get(host);
		}
		else {
			return 0;
		}
	}

	/**
	 * Updates transitive (A->B->C) delivery predictions.
	 * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
	 * </CODE>
	 * @param host The B host who we just met
	 */
	private void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof CustomEpidemicRouter : "CER only works " +
			" with other routers of same type";

		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds =
			((CustomEpidemicRouter)otherRouter).getDeliveryPreds();

		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; // don't add yourself
			}

			double pOld = getPredFor(e.getKey()); // P(a,c)_old
			double pNew = pOld + ( 1 - pOld) * pForHost * e.getValue() * beta;
			preds.put(e.getKey(), pNew);
		}
	}

	/**
	 * Ages all entries in the delivery predictions.
	 * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
	 * time units that have elapsed since the last time the metric was aged.
	 * @see #SECONDS_IN_UNIT_S
	 */
	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) /
			secondsInTimeUnit;

		if (timeDiff == 0) {
			return;
		}

		double mult = Math.pow(GAMMA, timeDiff);
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			e.setValue(e.getValue()*mult);
		}

		this.lastAgeUpdate = SimClock.getTime();
	}

	/**
	 * Returns a map of this router's delivery predictions
	 * @return a map of this router's delivery predictions
	 */
	private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.preds;
	}
	
	public DTNHost getDestHost() {
		return Message.getTo1();
	}
	
	public double p_to_reach_dest()
	{
		
		DTNHost othernode = Connection.getOtherNode1(getHost());
		MessageRouter othernodeRouter = othernode.getRouter();
		double p1;
		Map<DTNHost, Double> othersnodePreds =
				((CustomEpidemicRouter)othernodeRouter).getDeliveryPreds();
		
		for (Map.Entry<DTNHost, Double> e : othersnodePreds.entrySet()) {
			if (e.getKey() == getDestHost()) 
			{
				p1 = e.getValue();
				if(p1>=0 && p1<=1)
				{	
					return p1;
				}
			}
		}
		
		return 0;
	}
	
	
	/*
	 	private void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof CustomEpidemicRouter : "CER only works " +
			" with other routers of same type";

		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds =
			((CustomEpidemicRouter)otherRouter).getDeliveryPreds();

		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; // don't add yourself
			}

			double pOld = getPredFor(e.getKey()); // P(a,c)_old
			double pNew = pOld + ( 1 - pOld) * pForHost * e.getValue() * beta;
			preds.put(e.getKey(), pNew);
		}
	}
	 
	 */
	
	
	
	

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
		
		if((p_to_reach_dest() < thresholdProbability ) || (!hasSufficientEnergy()) || (getMessageSize() > getFreeBufferSize()))
		{	
			if(p_to_reach_dest() < thresholdProbability)
			{
				System.out.println("Probability to reach destination is below threshold probability!");
			}			
			if(!hasSufficientEnergy())
			{
				System.out.println("Energy level Not Available!");
				System.out.println(p_to_reach_dest());
			}
			if(getMessageSize() > getFreeBufferSize())
			{
				System.out.println("Required Buffer Space not Available!");
				System.out.println(p_to_reach_dest());
			}
			
			return;
  		}
		else 
		{
			System.out.println("Transmission going on!");
			System.out.println(p_to_reach_dest());
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
		 	//	System.out.println("Insufficient Energy! Value = " + this.getEnergy().getEnergy());
		 			return false;
		 		} else {
		 	//		System.out.println("Sufficient Energy! Value = " + this.getEnergy().getEnergy());
		 				return true;
		 			}
		 		} 
		 		return false;
		 	
	

}
}