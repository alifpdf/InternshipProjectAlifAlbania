package dataset;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Dataset {

    public static class WaterData {

        public float temperature;
        public float pH;
        public float orp;
        public int level;

        public WaterData(float temperature, float pH, float orp, int level) {
            this.temperature = temperature;
            this.pH = pH;
            this.orp = orp;
            this.level = level;
        }


    }

    public List<WaterData> waterData(String csvFileName) throws IOException {
        List<WaterData> waterData = new ArrayList<WaterData>();

        BufferedReader br = new BufferedReader(new FileReader((csvFileName)));

        while (br.readLine()!=null){
            String[] data = br.readLine().split(",");
            float temperature = Float.parseFloat(data[0]);
            float pH = Float.parseFloat(data[1]);
            float orp = Float.parseFloat(data[2]);
            int niveau = Integer.parseInt(data[3]);
            waterData.add(new WaterData(temperature, pH, orp, niveau));

        }
        return waterData;


    }

    public List<Integer> Calculate() throws IOException {
        List<WaterData> waterData = waterData("donnee.csv");
        int temperatureMean = 0;
        int pHMean = 0;
        int orpMean = 0;
        int levelMean = 0;
        for(WaterData wd : waterData){
            temperatureMean += wd.pH;
            pHMean += wd.pH;
            orpMean += wd.pH;
            levelMean += wd.pH;
        }
        temperatureMean /= waterData.size();
        pHMean /= waterData.size();
        orpMean /= waterData.size();
        levelMean /= waterData.size();

        return List.of(
                temperatureMean, pHMean, orpMean, (levelMean > 0 ? 1 : 0));

    }


}
