package org.matsim.project;

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

public class EmissionEventHandler
        implements WarmEmissionEventHandler, ColdEmissionEventHandler, ActivityEndEventHandler {

    private final Scenario scenario;
    private final Network network;
    Map<Tuple<Integer, Integer>, Map<Integer, Map<String, Double>>> grids = new HashMap<>();
    Map<String, Map<Tuple<Integer, Integer>, Double> > linkToGrid = new HashMap<>();
    // emissionElements = ['CO', 'NOx', 'PM', 'CO2_rep']
    List<String> emissionElements = List.of("CO", "NOx", "PM", "CO2_rep");
    Map<String, Integer> agent_last_act = new HashMap<>();

    // NetCDFReader netCDFReader = new NetCDFReader();

    Integer currentTimeStep = 0;
    static Lock lock = new ReentrantLock();

    // constructor
    public EmissionEventHandler(Scenario scenario) {
        this.scenario = scenario;
        this.network = scenario.getNetwork();

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

    public Map<Tuple<Integer, Integer>, Double> getOrCreateGrid(String linkId) {
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

        // to prevent errors, if y1 == y2 or x1 == x2 we add a small value
        if (y1 == y2) {
            y1 += 2;
        }
        if (x1 == x2) {
            x1 += 2;
        }

        int xGrid1 = (int) Math.floor(x1 / 10);
        int yGrid1 = (int) Math.floor(y1 / 10);
        int xGrid2 = (int) Math.floor(x2 / 10);
        int yGrid2 = (int) Math.floor(y2 / 10);

        double totalDistance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
        double slope = (y2 - y1) / (x2 - x1);
        double intercept = y1 - slope * x1;

        Map<Tuple<Integer, Integer>, Double> tileRatios = new HashMap<>();

        // Iterate over the tiles
        for (int x = Math.min(xGrid1, xGrid2); x <= Math.max(xGrid1, xGrid2); x++) {
            for (int y = Math.min(yGrid1, yGrid2); y <= Math.max(yGrid1, yGrid2); y++) {
                // Calculate the intersection points of the link with the tile boundaries
                double minX = x * 10;
                double maxX = minX + 10;
                double minY = y * 10;
                double maxY = minY + 10;

                double[] intersects = new double[4];

                // Check for intersections at tile boundaries
                if (x1 != x2) {
                    intersects[0] = slope * minX + intercept; // left edge
                    intersects[1] = slope * maxX + intercept; // right edge
                }

                if (y1 != y2) {
                    intersects[2] = (minY - intercept) / slope; // bottom edge
                    intersects[3] = (maxY - intercept) / slope; // top edge
                }

                Arrays.sort(intersects);

                double tileDistance = 0;

                for (int i = 0; i < 4; i++) {
                    if ((intersects[i] >= minX && intersects[i] <= maxX)
                            || (intersects[i] >= minY && intersects[i] <= maxY)) {
                        tileDistance += intersects[i];
                    }
                }

                tileDistance /= 2; // as we are summing up 2 times

                if (tileDistance > 0) {
                    double ratio = tileDistance / totalDistance;
                    tileRatios.put(new Tuple<Integer, Integer>(x, y), ratio);
                }
            }
        }
        
        linkToGrid.put(linkId, tileRatios);
        return tileRatios;
    }

    public Map<String, Double> getOrCreateEmissions(Map<Integer, Map<String, Double>> timeSteps, Integer timeStepKey) {
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
        // Map<Integer, Map<String, Double>> timeSteps = getOrCreateGrid(linkId.toString());
        Map<Tuple<Integer, Integer>, Double> tileRatios = getOrCreateGrid(linkId.toString());

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

        // for on each tileRatio
        for (Map.Entry<Tuple<Integer, Integer>, Double> tileRatio : tileRatios.entrySet()) {
            Tuple<Integer, Integer> gridCell = tileRatio.getKey();
            Double ratio = tileRatio.getValue();

            Map<Integer, Map<String, Double>> timeSteps = getOrCreateGrid(gridCell);

            Map<String, Double> emissions = getOrCreateEmissions(timeSteps, timeStep);
            for (String emissionElement : emissionElements) {
                if (eventEmissions.containsKey(Pollutant.valueOf(emissionElement))) {
                    Double emission = eventEmissions.get(Pollutant.valueOf(emissionElement));
                    Double currentEmission = emissions.get(emissionElement);
                    emissions.put(emissionElement, currentEmission + emission * ratio);
                }
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
    public void handleEvent(ActivityEndEvent event) {
        String agentId = event.getPersonId().toString();
        Integer lastAct = agent_last_act.getOrDefault(agentId, -1);
        Integer timeStep = (int) Math.floor(event.getTime() / 3600); // 1 Hour intervals
        // Integer timeStep = (int) Math.floor(event.getTime() / 300); // 5 Min
        // intervals
        double x = event.getCoord().getX();
        double y = event.getCoord().getY();
        Integer xGrid = (int) Math.floor(x / 10);
        Integer yGrid = (int) Math.floor(y / 10);
        Tuple<Integer, Integer> gridCell = new Tuple<>(xGrid, yGrid);

        for (int i = lastAct + 1; i <= timeStep; i++) {
            Map<String, Double> emissions = getOrCreateEmissions(getOrCreateGrid(gridCell), i);
            emissions.put("population", emissions.get("population") + 1);
        }
        agent_last_act.put(agentId, timeStep);
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
}
