package com.example.memgraph_api.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map; // Make sure this is imported!

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.memgraph_api.service.GraphService;
import com.example.memgraph_api.service.LlmExtractionService;

@RestController
@RequestMapping("/api/graph")
@CrossOrigin(origins = "*") // Allows React to talk to Java
public class GraphController {

    private final GraphService graphService;
    private final LlmExtractionService llmExtractionService;

    // THIS CONSTRUCTOR FIXES THE ERROR: It initializes both final variables
    public GraphController(GraphService graphService, LlmExtractionService llmExtractionService) {
        this.graphService = graphService;
        this.llmExtractionService = llmExtractionService;
    }

    @GetMapping
    public ResponseEntity<Map<String, List<?>>> getGraphData(@RequestParam(value = "identifier", required = false) String identifier){
        Map<String, List<?>> data = graphService.getGraphData(identifier);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadMedicalRecord(
            @RequestParam("file") MultipartFile file,
            @RequestParam("identifier") String identifier) {

        System.out.println("[UPLOAD] file: " + file.getOriginalFilename()
                + ", size: " + file.getSize()
                + ", identifier: " + identifier);

        try {
            // 1. Create the uploads directory if it doesn't exist
            Path uploadDir = Paths.get("uploads");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            
            // 2. Save the physical file to the disk
            String fileName = file.getOriginalFilename();
            Path filePath = uploadDir.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            llmExtractionService.processPdfAndStore(file, identifier);
            return ResponseEntity.ok("File processed and graph updated successfully!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error processing file: " + e.getMessage());
        }
    }
}