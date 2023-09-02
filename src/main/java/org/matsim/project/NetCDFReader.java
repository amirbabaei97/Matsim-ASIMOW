package org.matsim.project;

import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.Variable;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;

import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.GeotoolsTransformation;

import scala.Int;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.matsim.core.utils.collections.Tuple;

import java.io.FileWriter;
import java.io.IOException;

public class NetCDFReader {

    static CoordinateTransformation transformation = new GeotoolsTransformation("EPSG:4326", "EPSG:3035");

    // map time step -> {x/10000,y/10000} --> {u10, v10, t2m, sp}
    Map<Integer, Map<Tuple<Integer, Integer>, double[]>> data = new HashMap<>();

    // dispersion matrix for each grid
    Map<Tuple<Integer, Integer>, float[][]> dispersion_matrix = new HashMap<>();
    private Integer dispersion_n = 5;

    Set<Tuple<Double, Double>> coords = new HashSet<>();

    public void readNetcdfFile(String filePath) {
        try (NetcdfFile netcdfFile = NetcdfFiles.open(filePath)) {

            // Variables: longitude, latitude, time, u10, v10, t2m, sp
            Variable longitude = netcdfFile.findVariable("longitude");
            Variable latitude = netcdfFile.findVariable("latitude");
            Variable time = netcdfFile.findVariable("time");
            Variable u10 = netcdfFile.findVariable("u10");
            Variable v10 = netcdfFile.findVariable("v10");
            Variable t2m = netcdfFile.findVariable("t2m");
            Variable sp = netcdfFile.findVariable("sp");

            // read data
            Array longitudeData = longitude.read();
            Array latitudeData = latitude.read();
            Array timeData = time.read();
            Array u10Data = u10.read();
            Array v10Data = v10.read();
            Array t2mData = t2m.read();
            Array spData = sp.read();

            // for over all data
            Index timeIndex = timeData.getIndex();
            Index latitudeIndex = latitudeData.getIndex();
            Index longitudeIndex = longitudeData.getIndex();
            Index u10Index = u10Data.getIndex();
            Index v10Index = v10Data.getIndex();
            Index t2mIndex = t2mData.getIndex();
            Index spIndex = spData.getIndex();


            // scale_factor and add_offset attributes
            double u10ScaleFactor = u10.findAttribute("scale_factor").getNumericValue().doubleValue();
            double u10AddOffset = u10.findAttribute("add_offset").getNumericValue().doubleValue();
            double v10ScaleFactor = v10.findAttribute("scale_factor").getNumericValue().doubleValue();
            double v10AddOffset = v10.findAttribute("add_offset").getNumericValue().doubleValue();
            double t2mScaleFactor = t2m.findAttribute("scale_factor").getNumericValue().doubleValue();
            double t2mAddOffset = t2m.findAttribute("add_offset").getNumericValue().doubleValue();
            double spScaleFactor = sp.findAttribute("scale_factor").getNumericValue().doubleValue();
            double spAddOffset = sp.findAttribute("add_offset").getNumericValue().doubleValue();

            for (int t = 0; t < time.getShape(0); t++) {
                for (int lat = 0; lat < latitude.getShape(0); lat++) {
                    for (int lon = 0; lon < longitude.getShape(0); lon++) {
                        double u10Value = u10Data.getDouble(u10Index.set(t, lat, lon)) * u10ScaleFactor + u10AddOffset;
                        double v10Value = v10Data.getDouble(v10Index.set(t, lat, lon)) * v10ScaleFactor + v10AddOffset;
                        double t2mValue = t2mData.getDouble(t2mIndex.set(t, lat, lon)) * t2mScaleFactor + t2mAddOffset;
                        double spValue = spData.getDouble(spIndex.set(t, lat, lon)) * spScaleFactor + spAddOffset;

                        double latitudeValue = latitudeData.getDouble(latitudeIndex.set(lat));
                        double longitudeValue = longitudeData.getDouble(longitudeIndex.set(lon));
                        
                        Coord coord = transformation.transform(new Coord(longitudeValue, latitudeValue));
                        coords.add(new Tuple<>(coord.getX(), coord.getY()));
                        
                        this.data.putIfAbsent(t, new HashMap<>());
                        
                        int x = (int) coord.getX() / 10000;
                        int y = (int) coord.getY() / 10000;

                        this.data.get(t).putIfAbsent(new Tuple<>(x, y), new double[4]);
                        this.data.get(t).get(new Tuple<>(x, y))[0] = u10Value;
                        this.data.get(t).get(new Tuple<>(x, y))[1] = v10Value;
                        this.data.get(t).get(new Tuple<>(x, y))[2] = t2mValue;
                        this.data.get(t).get(new Tuple<>(x, y))[3] = spValue;
                        
                    }
                }
            }
            
            // write coords to csv file
            FileWriter csvWriter = new FileWriter("coords.csv");
            csvWriter.append("x");
            csvWriter.append(",");
            csvWriter.append("y");
            csvWriter.append("\n");
            for (Tuple<Double, Double> coord : coords) {
                csvWriter.append(coord.getFirst().toString());
                csvWriter.append(",");
                csvWriter.append(coord.getSecond().toString());
                csvWriter.append("\n");
            }
            csvWriter.flush();
            csvWriter.close();

            this.updateDispersionMatrix(0);
            System.out.println("Done reading NetCDF file");
        } catch (IOException e) {
            System.err.println("Error reading NetCDF file: " + e.getMessage());
        }
    }

    public double[] getValues(int timeStep, int x, int y) {
        return this.data.get(timeStep).get(new Tuple<>(x, y));
    }

    public void updateDispersionMatrix(int timeStep) {
        for (Tuple<Integer, Integer> key : this.data.get(timeStep).keySet()) {
            // Get the wind components at this grid point
            double u = this.data.get(timeStep).get(key)[0];
            double v = this.data.get(timeStep).get(key)[1];
    
            // Calculate wind direction and speed
            double windDirection = Math.toDegrees(Math.atan2(v, u));
            double windSpeed = Math.sqrt(u * u + v * v);
    
            // Calculate the dispersion matrix based on wind direction and speed
            float[][] dispersionMatrix = new float[this.dispersion_n][this.dispersion_n];
            for (int i = 0; i < this.dispersion_n; i++) {
                for (int j = 0; j < this.dispersion_n; j++) {
                    double distance = Math.sqrt((i - (this.dispersion_n - 1) / 2.0) * (i - (this.dispersion_n - 1) / 2.0)
                            + (j - (this.dispersion_n - 1) / 2.0) * (j - (this.dispersion_n - 1) / 2.0));
    
                    // Calculate the angle between the dispersion vector and the wind direction
                    double angle = Math.atan2(j - (this.dispersion_n - 1) / 2.0, i - (this.dispersion_n - 1) / 2.0)
                            - Math.toRadians(windDirection);
    
                    // Calculate the dispersion factor based on the wind speed and the angle
                    double dispersionFactor = windSpeed * Math.cos(angle);
    
                    // Update the dispersion matrix at this grid point
                    dispersionMatrix[i][j] = (float) Math.exp(-0.5 * distance * distance / (dispersionFactor * dispersionFactor));
                }
            }
    
            // Update the dispersion matrix for this grid point
            this.dispersion_matrix.put(key, dispersionMatrix);
        }
    }

    public float[][] getDispersionMatrix(int x, int y) {
        // check if dispersion matrix exists for this grid point
        if (this.dispersion_matrix.containsKey(new Tuple<>(x, y))) {
            return this.dispersion_matrix.get(new Tuple<>(x, y));
        } else {
            // return a gaussian dispersion matrix
            float[][] dispersionMatrix = new float[this.dispersion_n][this.dispersion_n];
            for (int i = 0; i < this.dispersion_n; i++) {
                for (int j = 0; j < this.dispersion_n; j++) {
                    double distance = Math.sqrt((i - (this.dispersion_n - 1) / 2.0) * (i - (this.dispersion_n - 1) / 2.0)
                            + (j - (this.dispersion_n - 1) / 2.0) * (j - (this.dispersion_n - 1) / 2.0));
                    dispersionMatrix[i][j] = (float) Math.exp(-0.5 * distance * distance / (1.0 * 1.0));
                }
            }
            return dispersionMatrix;
        }
    }
    
}
