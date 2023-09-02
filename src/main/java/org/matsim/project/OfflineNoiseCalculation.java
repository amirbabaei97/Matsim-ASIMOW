/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.project;

import java.util.Set;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.contrib.noise.NoiseConfigGroup;
import org.matsim.contrib.noise.NoiseOfflineCalculation;
import org.matsim.contrib.noise.MergeNoiseCSVFile;
import org.matsim.contrib.noise.ProcessNoiseImmissions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * 
 * An example how to compute noise levels, damages etc. for a single iteration
 * (= offline noise computation).
 * 
 * @author ikaddoura
 *
 */
public class OfflineNoiseCalculation {

	private static String runDirectory = "scenarios/ASIMOW/base/";
	private static String outputDirectory = "scenarios/ASIMOW/base/analysis-output-directory/";
	private static String runId = "ASIMOW";

	public static void main(String[] args) {

		Config config = ConfigUtils.loadConfig(runDirectory + runId + ".output_config.xml");

		// read network
		Network network = NetworkUtils.readNetwork(runDirectory + runId + ".output_network.xml.gz");
		Population population = PopulationUtils.readPopulation(runDirectory + runId + ".output_plans.xml.gz");

		config.controler().setOutputDirectory(runDirectory);

		// adjust the default noise parameters
		NoiseConfigGroup noiseParameters = new NoiseConfigGroup();
		noiseParameters.setBusIdIdentifierSet(Set.of("b_veh"));
		noiseParameters.setComputeNoiseDamages(true);
		noiseParameters.setAnnualCostRate(0.5);
		// home, work, hobby, leisure, shopping, education, visit, waiting
		noiseParameters.setConsideredActivitiesForDamageCalculationArray(
			new String[] { "home", "work", "hobby", "leisure", "shopping", "education", "visit", "waiting" });
		noiseParameters.setScaleFactor(13.0);
		noiseParameters.setReceiverPointGap(500);
		noiseParameters.setRelevantRadius(500);
		noiseParameters.setTimeBinSizeNoiseComputation(3600);
		// 4213400, 2988200, 4235300, 3010800
		noiseParameters.setReceiverPointsGridMinX(4213400);
		noiseParameters.setReceiverPointsGridMinY(2988200);
		noiseParameters.setReceiverPointsGridMaxX(4235300);
		noiseParameters.setReceiverPointsGridMaxY(3010800);
		noiseParameters.setReceiverPointsCSVFileCoordinateSystem("EPSG:3035");
		noiseParameters.setComputePopulationUnits(true);

		// ...

		Scenario scenario = new ScenarioUtils.ScenarioBuilder(config).setNetwork(network).setPopulation(population)
				.build();

		NoiseOfflineCalculation noiseCalculation = new NoiseOfflineCalculation(scenario, outputDirectory);
		noiseCalculation.run();

		// some processing of the output data
		if (!outputDirectory.endsWith("/"))
			outputDirectory = outputDirectory + "/";

		String outputFilePath = outputDirectory + "noise-analysis/";
		ProcessNoiseImmissions process = new ProcessNoiseImmissions(outputFilePath + "immissions/",
				outputFilePath + "receiverPoints/receiverPoints.csv", noiseParameters.getReceiverPointGap());
		process.run();

		final String[] labels = { "immission", "consideredAgentUnits", "damages_receiverPoint" };
		final String[] workingDirectories = { outputFilePath + "/immissions/",
				outputFilePath + "/consideredAgentUnits/", outputFilePath + "/damages_receiverPoint/" };

		MergeNoiseCSVFile merger = new MergeNoiseCSVFile();
		merger.setReceiverPointsFile(outputFilePath + "receiverPoints/receiverPoints.csv");
		merger.setOutputDirectory(outputFilePath);
		merger.setTimeBinSize(noiseParameters.getTimeBinSizeNoiseComputation());
		merger.setWorkingDirectory(workingDirectories);
		merger.setLabel(labels);
		merger.run();
	}
}
