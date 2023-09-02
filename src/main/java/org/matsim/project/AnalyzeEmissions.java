package org.matsim.project;

import org.matsim.contrib.emissions.analysis.EmissionGridAnalyzer;
import org.matsim.contrib.emissions.utils.EmissionsConfigGroup;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.json.JSONObject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.analysis.time.TimeBinMap;
import org.matsim.contrib.emissions.EmissionModule;
import org.matsim.contrib.emissions.EmissionUtils;
import org.matsim.contrib.emissions.HbefaRoadTypeMapping;
import org.matsim.contrib.emissions.OsmHbefaMapping;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.events.handler.EventHandler;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.contrib.emissions.events.EmissionEventsReader;
import com.google.inject.Injector;

import org.matsim.contrib.analysis.spatial.Grid;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.collections.Tuple;

import org.matsim.core.network.NetworkUtils;
import org.matsim.api.core.v01.Id;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.lang.Math;

import org.json.JSONObject;
import java.io.FileWriter;
import java.io.IOException;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.matsim.contrib.emissions.events.*;
import org.matsim.contrib.emissions.Pollutant;

import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;

public class AnalyzeEmissions {

    public static class EmissionEventHandler
            implements WarmEmissionEventHandler, ColdEmissionEventHandler {
        // implements BasicEventHandler {

        private final Network network;
        Map<Tuple<Integer, Integer>, Map<Integer, Map<String, Double>>> grids = new HashMap<>();
        Map<String, Tuple<Integer, Integer>> linkToGrid = new HashMap<>();
        // emissionElements = ['CO', 'NOx', 'PM', 'CO2_rep']
        List<String> emissionElements = List.of("CO", "NOx", "PM", "CO2_rep");
        Map<String, Integer> agent_last_act = new HashMap<>();

        // NetCDFReader netCDFReader = new NetCDFReader();

        Integer currentTimeStep = 0;
        static Lock lock = new ReentrantLock();

        // constructor
        public EmissionEventHandler(Network network) {
            this.network = network;

            // netCDFReader.readNetcdfFile("my_scenario/data1.nc");

        }

        /**
         * Returns the grid cell for the given link ID or creates a new grid cell if it
         * does not exist.
         *
         * @param linkId The link ID for which the grid cell should be returned or
         *               created.
         * @return A map representing the grid cell.
         */
        public Map<Integer, Map<String, Double>> getOrCreateGrid(Tuple<Integer, Integer> gridCell) {
            if (grids.containsKey(gridCell)) {
                return grids.get(gridCell);
            } else {
                Map<Integer, Map<String, Double>> timeSteps = new HashMap<>();
                grids.put(gridCell, timeSteps);
                return timeSteps;
            }
        }

        public Tuple<Integer, Integer> getOrCreateGrid(String linkId) {
            // Get the link's coordinates
            if (linkToGrid.containsKey(linkId)) {
                return linkToGrid.get(linkId);
            }

            Link link = network.getLinks().get(Id.create(linkId, Link.class));
            Node fromNode = link.getFromNode();
            Node toNode = link.getToNode();

            double x1 = fromNode.getCoord().getX();
            double y1 = fromNode.getCoord().getY();
            double x2 = toNode.getCoord().getX();
            double y2 = toNode.getCoord().getY();

            // Calculate the grid cell coordinates
            double x = (x1 + x2) / 2;
            double y = (y1 + y2) / 2;

            Integer xGrid = (int) Math.floor(x / 100);
            Integer yGrid = (int) Math.floor(y / 100);

            Tuple<Integer, Integer> gridCell = new Tuple<>(xGrid, yGrid);
            linkToGrid.put(linkId, gridCell);
            return gridCell;
        }

        public Map<String, Double> getOrCreateEmissions(Map<Integer, Map<String, Double>> timeSteps,
                Integer timeStepKey) {
            if (timeSteps.containsKey(timeStepKey)) {
                return timeSteps.get(timeStepKey);
            } else {
                Map<String, Double> emissions = new HashMap<>();
                for (String emissionElement : emissionElements) {
                    emissions.put(emissionElement, 0.0);
                }
                emissions.put("population", 0.0);
                timeSteps.put(timeStepKey, emissions);
                return emissions;
            }
        }

        public void handleEvent(EmissionEvent event) {
            // Get the link ID from the event
            Id<Link> linkId = event.getLinkId();
            // Map<Integer, Map<String, Double>> timeSteps =
            // getOrCreateGrid(linkId.toString());
            Tuple<Integer, Integer> grid = getOrCreateGrid(linkId.toString());

            // Get the time step from the event
            // Integer timeStep = (int) Math.floor(event.getTime() / 300); // 5 Min
            // intervals
            Integer timeStep = (int) Math.floor(event.getTime() / 3600); // 1 Hour intervals

            lock.lock();
            if (timeStep > currentTimeStep) {
                currentTimeStep = timeStep + 1;
            }
            lock.unlock();

            Map<Pollutant, Double> eventEmissions = event.getEmissions();

            Map<Integer, Map<String, Double>> timeSteps = getOrCreateGrid(grid);

            Map<String, Double> emissions = getOrCreateEmissions(timeSteps, timeStep);
            for (String emissionElement : emissionElements) {
                if (eventEmissions.containsKey(Pollutant.valueOf(emissionElement))) {
                    emissions.put(emissionElement, emissions.get(emissionElement)
                            + eventEmissions.get(Pollutant.valueOf(emissionElement)));
                }
            }

        }

        @Override
        public void handleEvent(WarmEmissionEvent event) {
            handleEvent((EmissionEvent) event);
        }

        @Override
        public void handleEvent(ColdEmissionEvent event) {
            handleEvent((EmissionEvent) event);
        }

        @Override
        public void reset(int iteration) {
            if (iteration % 10 == 0) {
                System.out.println("Iteration: " + iteration);
                writeToJson("output/emissions_" + iteration + ".json");
            }
            grids.clear();
            linkToGrid.clear();
            agent_last_act.clear();
        }

        public void writeToJson(String path) {
            JSONObject json = new JSONObject();
            for (Tuple<Integer, Integer> gridCell : grids.keySet()) {
                JSONObject gridCellJson = new JSONObject();
                for (Integer timeStep : grids.get(gridCell).keySet()) {
                    JSONObject timeStepJson = new JSONObject();
                    for (String emissionElement : emissionElements) {
                        // round to 4 decimal places
                        timeStepJson.put(emissionElement,
                                Math.round(grids.get(gridCell).get(timeStep).get(emissionElement) * 10000.0) / 10000.0);
                    }
                    timeStepJson.put("population",
                            Math.round(grids.get(gridCell).get(timeStep).get("population") * 10000.0) / 10000.0);
                    gridCellJson.put(timeStep.toString(), timeStepJson);
                }
                String gridCellString = gridCell.getFirst().toString() + "," + gridCell.getSecond().toString();
                json.put(gridCellString, gridCellJson);
            }
            // beautify json
            String jsonString = json.toString(4);
            try (FileWriter file = new FileWriter(path)) {
                file.write(jsonString);
                file.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Example output:
            // {
            // "0,0": {
            // "0": {
            // "CO": 0.0,
            // "NOx": 0.0,
            // ...
            // "population": 1.0
            // },
        }

        // @Override
        // public void handleEvent(Event event) {
        // if (event.getEventType().toString().toLowerCase().contains("emission")) {
        // handleEvent((EmissionEvent) event);
        // }
        // }
    }

    public static void main(String[] args) {

        String networkFile = "output_base_last/output_network.xml.gz";
        String eventsFile = "output_base_last/output_events.xml.gz";

        Config config = ConfigUtils.loadConfig("my_scenario/config_new.xml",
                new EmissionsConfigGroup());

        // read network
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(networkFile);
        final EventsManager eventsManager = EventsUtils.createEventsManager();

        // final Scenario scenario = ScenarioUtils.loadScenario(config);
        // AbstractModule module = new AbstractModule() {
        // public void install() {
        // this.bind(Scenario.class).toInstance(scenario);
        // this.bind(EventsManager.class).toInstance(eventsManager);
        // this.bind(EmissionModule.class);
        // }
        // };

        // Injector injector = org.matsim.core.controler.Injector.createInjector(config,
        // module);
        // EmissionModule emissionModule = injector.getInstance(EmissionModule.class);

        EmissionEventHandler emissionEventHandler = new EmissionEventHandler(network);

        eventsManager.addHandler(emissionEventHandler);

        // run events manager
        // EventsUtils.readEvents(eventsManager, eventsFile);
        // EventsUtils.readEvents(emissionModule.getEmissionEventsManager(),
        // eventsFile);

        EmissionEventsReader reader = new EmissionEventsReader(eventsManager);
        reader.readFile(eventsFile);

        emissionEventHandler.writeToJson("output_base_last/emissions.json");

    }
}