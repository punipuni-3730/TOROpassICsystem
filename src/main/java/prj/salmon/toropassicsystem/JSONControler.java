package prj.salmon.toropassicsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import prj.salmon.toropassicsystem.types.SavingDataJson;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class JSONControler {
    private final String filename;
    private final File datafolder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JSONControler(String filename, File datafolder) {
        this.filename = filename;
        this.datafolder = datafolder;
    }

    public void initialiseIfNotExists() throws IOException {
        File file = new File(datafolder + File.pathSeparator + filename);

        if (!file.exists()) {
            file.createNewFile();

            SavingDataJson data = new SavingDataJson();
            data.data = new ArrayList<>();

            save(data);
        }
    }

    public void save(SavingDataJson data) throws IOException {
        File file = new File(datafolder + File.pathSeparator + filename);
        data.lastupdate = System.currentTimeMillis() / 1000L;

        objectMapper.writeValue(file, data);
    }

    public SavingDataJson load() throws IOException {
        return objectMapper.readValue(new File(datafolder + File.pathSeparator + filename), SavingDataJson.class);
    }
}
