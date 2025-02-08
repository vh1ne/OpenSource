package com.vh1ne.OpenSource;


import com.vh1ne.OpenSource.xpnl.FileProcessorService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@Log4j2
@RestController("/base")
public class BaseController {


    @Autowired
    private FileProcessorService fileProcessorService;

    @GetMapping("/gen")
    public String processFile() {
        try {
            String inputFilePath="";
            String outputFilePath ="D:\\Projects\\spring-boot-docker\\genrated_resp_300k.txt";
           //
            long startTime = System.nanoTime();
         var gen=   fileProcessorService.generate300k();
            long endTime = System.nanoTime(); // End time
            double elapsedTimeInSeconds = (endTime - startTime) / 1_000_000_000.0; // Convert nanoseconds to seconds

            log.info("Execution time:  for generate300k {} seconds", elapsedTimeInSeconds);
            log.info("generated file {}", gen);
            startTime = System.nanoTime();
            fileProcessorService.processFile(gen, outputFilePath);
            endTime = System.nanoTime(); // End time
             elapsedTimeInSeconds = (endTime - startTime) / 1_000_000_000.0; // Convert nanoseconds to seconds

            //  log.info("Execution time:  for processFile {} seconds", elapsedTimeInSeconds);
            return "File processing started. Check logs for progress.";
        } catch (Exception e) {
            return "Failed to start file processing: " + e.getMessage();
        }
    }
}
