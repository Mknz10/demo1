package com.example.memgraph_api.service;

import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyDoctorOfAccessChange(String doctorId, Map<String, String> details) {
        String destination = "/topic/doctor/" + doctorId;
        Map<String, Object> message = Map.of("type", "ACCESS_REVOKED", "details", details);
        messagingTemplate.convertAndSend(destination, message);
        System.out.println("Sent ACCESS_REVOKED notification to " + destination + " for patient " + details.get("patientId"));
    }

    public void notifyDoctorOfAccessGranted(String doctorId, Map<String, Object> patientDetails) {
        String destination = "/topic/doctor/" + doctorId;
        Map<String, Object> message = Map.of("type", "ACCESS_GRANTED", "patient", patientDetails);
        messagingTemplate.convertAndSend(destination, message);
        System.out.println("Sent ACCESS_GRANTED notification to " + destination + " for patient " + patientDetails.get("id"));
    }
}