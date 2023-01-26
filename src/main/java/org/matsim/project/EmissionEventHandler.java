package org.matsim.project;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.network.NetworkUtils;
import org.matsim.api.core.v01.Id;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import java.io.FileWriter;
import java.io.IOException;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EmissionEventHandler implements BasicEventHandler {

    private final Scenario scenario;
    private final Network network;
    Map<Tuple<Integer, Integer>, Map<Integer, Map<String, Double>>> grids = new HashMap<>();
    Map<String, Tuple<Integer, Integer>> linkToGrid = new HashMap<>();
    // emission_elements = ['CO', 'NOx', 'PM', 'CO2_TOTAL']
    List<String> emission_elements = List.of("CO", "NOx", "PM", "CO2_TOTAL");
    Map<String, Integer> agent_last_act = new HashMap<>();

    Integer currentStep = 0;
    static Lock lock = new ReentrantLock();

    // a 2D matrix of dispersion grids
    private Integer dispersion_n = 9;
    private float[][] dispersion_grid = new float[dispersion_n][dispersion_n];
    private float center_value = 0.5f;
    private float sigma = 1;

    // constructor
    public EmissionEventHandler(Scenario scenario) {
        this.scenario = scenario;
        this.network = scenario.getNetwork();

        // initialize the dispersion grid using gaussian distribution
        // center of dispersion grid has the highest value (0.5)
        // the value decreases as the distance from the center increases

        for (int i = 0; i < dispersion_n; i++) {
            for (int j = 0; j < dispersion_n; j++) {
                float distance_from_center = (float) Math
                        .sqrt(Math.pow(i - (dispersion_n - 1) / 2f, 2) + Math.pow(j - (dispersion_n - 1) / 2f, 2));
                dispersion_grid[i][j] = (float) (center_value
                        * Math.exp(-0.5 * Math.pow(distance_from_center, 2) / Math.pow(sigma, 2)));
            }
        }
    }

    public Map<Integer, Map<String, Double>> getOrCreateGrid(Tuple<Integer, Integer> gridCell) {
        if (grids.containsKey(gridCell)) {
            return grids.get(gridCell);
        } else {
            Map<Integer, Map<String, Double>> timeSteps = new HashMap<>();
            grids.put(gridCell, timeSteps);
            return timeSteps;
        }
    }

    public Map<Integer, Map<String, Double>> getOrCreateGrid(String linkId) {
        // Get the link's coordinates
        if (linkToGrid.containsKey(linkId)) {
            return getOrCreateGrid(linkToGrid.get(linkId));
        }

        Link link = network.getLinks().get(Id.create(linkId, Link.class));
        Node fromNode = link.getFromNode();
        Node toNode = link.getToNode();
        double x = (fromNode.getCoord().getX() + toNode.getCoord().getX()) / 2;
        double y = (fromNode.getCoord().getY() + toNode.getCoord().getY()) / 2;
        Integer xGrid = (int) Math.floor(x / 10);
        Integer yGrid = (int) Math.floor(y / 10);
        Tuple<Integer, Integer> gridCell = new Tuple<>(xGrid, yGrid);
        linkToGrid.put(linkId, gridCell);
        return getOrCreateGrid(gridCell);
    }

    public Map<String, Double> getOrCreateEmissions(Map<Integer, Map<String, Double>> timeSteps, Integer timeStepKey) {
        if (timeSteps.containsKey(timeStepKey)) {
            return timeSteps.get(timeStepKey);
        } else {
            Map<String, Double> emissions = new HashMap<>();
            for (String emissionElement : emission_elements) {
                emissions.put(emissionElement, 0.0);
            }
            emissions.put("population", 0.0);
            timeSteps.put(timeStepKey, emissions);
            return emissions;
        }
    }

    private void dispereEmissions(int timeStep) {
        int nextTimeStep = timeStep + 1;
        try {
            for (Tuple<Integer, Integer> gridCell : grids.keySet()) {
                Map<Integer, Map<String, Double>> timeSteps = grids.get(gridCell);
                if (timeSteps.containsKey(timeStep)) {
                    Map<String, Double> emissions = timeSteps.get(timeStep);
                    for (int i = 0; i < dispersion_n; i++) {
                        for (int j = 0; j < dispersion_n; j++) {
                            Tuple<Integer, Integer> newGridCell = new Tuple<>(
                                    gridCell.getFirst() + i - (dispersion_n - 1) / 2,
                                    gridCell.getSecond() + j - (dispersion_n - 1) / 2);
                            Map<String, Double> newEmissions = getOrCreateEmissions(getOrCreateGrid(newGridCell),
                                    nextTimeStep);
                            for (String emissionElement : emission_elements) {
                                newEmissions.put(emissionElement, newEmissions.get(emissionElement)
                                        + emissions.get(emissionElement) * dispersion_grid[i][j]);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Override
    public void handleEvent(Event event) {
        // Check if the event is a cold or warm emission event
        if (event.getEventType().contains("Emission")) {
            Map<String, String> attributes = event.getAttributes();
            // Get the link ID from the event
            String linkId = attributes.get("linkId");
            Map<Integer, Map<String, Double>> timeSteps = getOrCreateGrid(linkId);

            // Get the time step from the event
            Integer timeStep = (int) Math.floor(event.getTime() / 3600);

            lock.lock();
            if (timeStep > currentStep) {
                dispereEmissions(currentStep);
                currentStep = timeStep;
            }
            lock.unlock();

            for (String emissionElement : emission_elements) {
                if (attributes.containsKey(emissionElement)) {
                    Double emission = Double.parseDouble(attributes.get(emissionElement));
                    Double currentEmission = getOrCreateEmissions(timeSteps, timeStep).get(emissionElement);
                    getOrCreateEmissions(timeSteps, timeStep).put(emissionElement, currentEmission + emission);
                }
            }
        } else if (event.getEventType().contains("actend")) {
            Map<String, String> attributes = event.getAttributes();
            String agentId = attributes.get("person");
            Integer lastAct = agent_last_act.getOrDefault(agentId, -1);
            Integer timeStep = (int) Math.floor(event.getTime() / 3600);
            double x = Double.parseDouble(attributes.get("x"));
            double y = Double.parseDouble(attributes.get("y"));
            Integer xGrid = (int) Math.floor(x / 10);
            Integer yGrid = (int) Math.floor(y / 10);
            Tuple<Integer, Integer> gridCell = new Tuple<>(xGrid, yGrid);

            for (int i = lastAct + 1; i <= timeStep; i++) {
                Map<String, Double> emissions = getOrCreateEmissions(getOrCreateGrid(gridCell), i);
                emissions.put("population", emissions.get("population") + 1);
            }
            agent_last_act.put(agentId, timeStep);
        }
    }

    @Override
    public void reset(int iteration) {
        grids.clear();
        linkToGrid.clear();
    }

    public void writeToJson(String path) {
        JSONObject json = new JSONObject();
        for (Tuple<Integer, Integer> gridCell : grids.keySet()) {
            JSONObject gridCellJson = new JSONObject();
            for (Integer timeStep : grids.get(gridCell).keySet()) {
                JSONObject timeStepJson = new JSONObject();
                for (String emissionElement : emission_elements) {
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
