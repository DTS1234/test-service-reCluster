package it.unitn.test.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class Controller {

    public static final String PATH = "/Users/adamkazmierczak/sys/devices/system/cpu/";

    @GetMapping("/params")
    public TestParams getTestParams() throws IOException {

        Set<String> fileSet = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(PATH))) {
            for (Path dir : stream) {
                if (Files.isDirectory(dir)) {
                    fileSet.add(dir.getFileName()
                        .toString());
                }
            }
        }

        TestParams params = new TestParams(new ArrayList<>());

        fileSet
            .forEach(cpu -> {

                Map<String, String> map = new HashMap<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(PATH + cpu + "/cpufreq"))) {
                    for (Path dir : stream) {
                        if (!Files.isDirectory(dir)) {
                            if (!dir.getFileName().toString().contains("DS")) {
                                map.put(dir.getFileName().toString(), Files.readString(dir).trim());
                            }
                        }
                    }
                    params.getCpuInfoList().add(new CPUinfo(cpu, map));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            });

        return params;
    }

    @PostMapping("/test")
    public void startTest(@RequestBody TestSpecification spec) throws IOException {

        // set params from the spec
        List<String> cpusNames = spec.getCpuTestSpecs().stream().map(CPUTestSpec::getName).toList();

        cpusNames.forEach(cpu -> {
            CPUTestSpec cpuTestSpec = findCpuByName(spec, cpu);

            try (DirectoryStream<Path> paramsForCPu = Files.newDirectoryStream(Paths.get(PATH + cpu))) {
                for (Path currentParam : paramsForCPu) {
                    if (!Files.isDirectory(currentParam)) {

                        if (!currentParam.getFileName().toString().contains("DS")) {

                            if (cpuTestSpec != null){

                                // clear file
                                Files.write(currentParam, new byte[0]);
                                String paramToSetValue = cpuTestSpec.getTestParams().get(currentParam.getFileName().toString());
                                System.out.printf("setting %s, for parameter: %s%n", paramToSetValue, currentParam.getFileName().toString());

                                // overwrite the parameter
                                Files.writeString(currentParam, paramToSetValue);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        System.out.println("TEST START !!!");

        // create a sysbench/stress ng command
        int numCores = Runtime.getRuntime().availableProcessors();
        String command = String.format("stress-ng --cpu %s --cpu-load %s -t %s", numCores, spec.getCpuLoad(), spec.getDurationInSeconds());

        // start a command for time specified and cpu load
        Process process = Runtime.getRuntime().exec(command);

        // return the test result
        System.out.println("TEST FINISHED !!!");
    }

    private static CPUTestSpec findCpuByName(TestSpecification spec, String cpu) {
        return spec.getCpuTestSpecs().stream().filter(cpuNumber -> cpuNumber.getName().equals(cpu)).findFirst()
            .orElse(null);
    }
}

@Data
@AllArgsConstructor
class TestSpecification{
    private Long durationInSeconds;
    private Integer cpuLoad;
    private List<CPUTestSpec> cpuTestSpecs;
}

@Data
@AllArgsConstructor
class CPUTestSpec {
    private String name;
    private Map<String, String> testParams;
}

@Data
@AllArgsConstructor
class TestResult {
    //in Watts
    private String averagePowerConsumption;
    private String maxPowerConsumption;
    private String minPowerConsumption;
}

@Data
@AllArgsConstructor
class TestParams {
    private List<CPUinfo> cpuInfoList;
}

@Data
@AllArgsConstructor
class CPUinfo {
    private String name;
    private Map<String, String> params;
}
